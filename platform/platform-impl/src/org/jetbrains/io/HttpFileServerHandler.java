package org.jetbrains.io;

import com.intellij.util.Consumer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedFile;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

@ChannelHandler.Sharable
public class HttpFileServerHandler extends SimpleChannelUpstreamHandler implements Consumer<ChannelPipeline> {
  private static final int HTTP_CACHE_SECONDS = 60;
  private static final MimetypesFileTypeMap FILE_MIMETYPE_MAP = new MimetypesFileTypeMap();

  private static final DateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);

  static {
    FILE_MIMETYPE_MAP.addMimeTypes("application/javascript js");
    FILE_MIMETYPE_MAP.addMimeTypes("application/x-shockwave-flash swf");
    FILE_MIMETYPE_MAP.addMimeTypes("application/x-chrome-extension crx");
    FILE_MIMETYPE_MAP.addMimeTypes("text/css css");

    DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  private final File[] sourceRoots;

  public HttpFileServerHandler(File... sourceRoots) {
    this.sourceRoots = sourceRoots;
  }

  @Override
  public void consume(ChannelPipeline pipeline) {
    pipeline.addLast("httpStaticFileHandler", this);
  }

  @Override
  public void messageReceived(ChannelHandlerContext context, MessageEvent e) throws Exception {
    if (e.getMessage() instanceof HttpRequest) {
      HttpRequest request = (HttpRequest)e.getMessage();
      if (request.getMethod() == HttpMethod.GET) {
        QueryStringDecoder uriDecoder = new QueryStringDecoder(request.getUri());
        if (process(uriDecoder, uriDecoder.getPath(), request, context)) {
          return;
        }
      }
    }

    context.sendUpstream(e);
  }

  protected boolean process(QueryStringDecoder uriDecoder, String path, HttpRequest request, ChannelHandlerContext context) throws IOException {
    if (path.length() == 1 && path.charAt(0) == '/') {
      path = "index.html";
    }

    for (File sourceRoot : sourceRoots) {
      File file = new File(sourceRoot, path);
      if (file.isFile() && !file.isHidden()) {
        writeFile(request, context, file);
        return true;
      }
    }

    return false;
  }

  protected static HttpResponse createResponse(String path) {
    final HttpResponse response = Responses.create(FILE_MIMETYPE_MAP.getContentType(path));
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Credentials", true);
    return response;
  }

  protected static void writeFile(HttpRequest request, ChannelHandlerContext context, File file) throws IOException {
    // Cache Validation
    String ifModifiedSince = request.getHeader(IF_MODIFIED_SINCE);
    if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
      try {
        if (DATE_FORMAT.parse(ifModifiedSince).getTime() >= file.lastModified()) {
          HttpResponse response = new DefaultHttpResponse(HTTP_1_1, NOT_MODIFIED);
          response.setHeader("Access-Control-Allow-Origin", "*");
          response.setHeader("Access-Control-Allow-Credentials", true);
          setDate(response);
          Responses.send(response, request, context);
          return;
        }
      }
      catch (ParseException ignored) {
      }
    }

    boolean fileWillBeClosed = false;
    RandomAccessFile raf = new RandomAccessFile(file, "r");
    try {
      long fileLength = raf.length();
      HttpResponse response = createResponse(file.getPath());
      setContentLength(response, fileLength);
      setDateAndCacheHeaders(response, file);
      if (isKeepAlive(request)) {
        response.setHeader(CONNECTION, Values.KEEP_ALIVE);
      }

      Channel channel = context.getChannel();
      channel.write(response);

      ChannelFuture future;
      if (channel.getPipeline().get(SslHandler.class) == null) {
        // No encryption - use zero-copy.
        final FileRegion region = new DefaultFileRegion(raf.getChannel(), 0, fileLength);
        future = channel.write(region);
        future.addListener(new ChannelFutureListener() {
          @Override
          public void operationComplete(ChannelFuture future) {
            region.releaseExternalResources();
          }
        });
      }
      else {
        // Cannot use zero-copy with HTTPS.
        future = channel.write(new ChunkedFile(raf, 0, fileLength, 8192));
      }

      if (!isKeepAlive(request)) {
        future.addListener(ChannelFutureListener.CLOSE);
      }

      fileWillBeClosed = true;
    }
    finally {
      if (!fileWillBeClosed) {
        raf.close();
      }
    }
  }

  private static void setDateAndCacheHeaders(HttpResponse response, File file) {
    Calendar time = setDate(response);
    time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
    response.setHeader(EXPIRES, DATE_FORMAT.format(time.getTime()));
    response.setHeader(CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
    response.setHeader(LAST_MODIFIED, DATE_FORMAT.format(new Date(file.lastModified())));
  }

  private static Calendar setDate(HttpResponse response) {
    Calendar time = new GregorianCalendar();
    response.setHeader(DATE, DATE_FORMAT.format(time.getTime()));
    return time;
  }
}
