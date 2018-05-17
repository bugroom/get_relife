package com.legend.getrelife;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Downloader {

    private WebClient webClient;
    private static final String MAIN_URL = "https://relifelab.gitlab.io/labreport";//主页

    private static final String ARTICLE_PAGER="https://relifelab.gitlab.io";

    private List<Article> articleList;


    private int CORE_COUNT = Runtime.getRuntime().availableProcessors();
    private int CORE_POOL_SIZE = CORE_COUNT + 1;
    private int CORE_POOL_MAX_SIZE = CORE_COUNT * 2 + 1;
    private int KEEP_ALIVE = 10;

    private ThreadPoolExecutor poolExecutor;




    public Downloader() {
        init();

        BlockingQueue<Runnable> runnableBlockingQueue = new LinkedBlockingQueue<>();
        poolExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, CORE_POOL_MAX_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, runnableBlockingQueue);

    }

    private void init() {
        this.webClient = new WebClient(BrowserVersion.CHROME);//假装自己是个浏览器

        this.webClient.setJavaScriptTimeout(100000);//需要运行js，所以给足js的运行时间

        this.webClient.getOptions().setTimeout(100000);//网页超时

        this.webClient.getOptions().setCssEnabled(false);//不需要css样式
        this.webClient.getOptions().setJavaScriptEnabled(true);//打开js
        this.webClient.getOptions().setThrowExceptionOnScriptError(false);
        this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
    }

    public void startDownload() {

        parseMain();//解析主页

        start();//开始下载：分为两步，一是解析章节页获取图片真正的URL，二是以真正的URL获取流并保存在本地
    }

    /**
     * 获取HTML页面
     */
    private String getPager(String url) {

        String html="";

        try {

            HtmlPage pager = this.webClient.getPage(url);

//            System.out.println(pager.asXml());

            html=pager.asXml();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return html;

    }

    /**
     * 解析主页信息
     */
    private void parseMain(){

        this.articleList=new ArrayList<>();

        String mainPager=getPager(MAIN_URL);

        Document document=Jsoup.parse(mainPager);

        Elements h1s=document.getElementsByClass("post__title archive");//获取名为post__title archive的所有H1标签

        for (Element element:h1s){

            Elements a=element.getElementsByTag("a");//获取其下的a标签

            String url=a.get(0).attr("href");//获取url

            Elements span=a.get(0).getElementsByTag("span");

            String name=span.get(0).text();//获取章节名称

            Article article=new Article();

            article.setName(name);
            article.setUrl(url);

            System.out.println(" name-->>"+name+"\n url--->>"+url);

            this.articleList.add(article);//将封装好的数据放入数组内

        }
    }

    private void start(){

        Runnable runnable=()->{

            if (this.articleList!=null){

                for (Article article:articleList){

                    download(article);
                }
            }
        };

        poolExecutor.execute(runnable);

    }

    /**
     * 解析章节页面，获取图片真正的URL
     * @param article
     * @return
     */
    private String parseImg(Article article){

        String url="";

        String content_url=ARTICLE_PAGER+article.getUrl();


        String content_html=getPager(content_url);

//        System.out.println(content_url);

        if (content_html.equals("")){
            return "";
        }

        Document document=Jsoup.parse(content_html);

        Elements elements=document.getElementsByTag("img");


        for (Element img:elements){

            url=img.attr("src");

        }


        return url;

    }

    private void download(Article article){

        String url=parseImg(article);

        System.out.println(url);

        if (url.equals("")){//避免非正常url
            return;
        }

        try {
            Request.Builder builder1 = new Request.Builder().url(url).method("GET", null);

            Request request = builder1.build();

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true);

            OkHttpClient okHttpClient = builder.build();

            Call call = okHttpClient.newCall(request);

            Response response = call.execute();

            String type = response.header("Content-Type");

            if (type != null && type.startsWith("image/")) {

                String path = "/Volumes/download/relife漫画版/";

                String image_path = path + article.getName() + ".jpg" ;

                File file = new File(image_path);

                System.out.println("" + file.getName());

                FileOutputStream fileOutputStream = new FileOutputStream(file);

                FileChannel fileChannel = fileOutputStream.getChannel();

                ByteBuffer buffer = ByteBuffer.allocate(2048);


                byte[] bytes = response.body().bytes();

                for (byte aByte : bytes) {

                    if (buffer.hasRemaining()) {
                        buffer.put(aByte);
                    } else {
                        //如果buffer满了，则写入文件
                        buffer.flip();
                        fileChannel.write(buffer);
                        buffer.compact();
                        buffer.put(aByte);
                    }
                }

                //将剩余的也一并写入文件
                buffer.flip();
                fileChannel.write(buffer);
                buffer.clear();

                fileChannel.close();
                fileOutputStream.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
