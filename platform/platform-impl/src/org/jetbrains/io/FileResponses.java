/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.io;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedFile;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.util.Date;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.jetbrains.io.Responses.*;

public class FileResponses {
  private static final MimetypesFileTypeMap FILE_MIMETYPE_MAP = new MimetypesFileTypeMap();

  public static HttpResponse createResponse(String path) {
    HttpResponse response = create(FILE_MIMETYPE_MAP.getContentType(path));
    response.setHeader(CACHE_CONTROL, "max-age=0");
    return response;
  }

  private static boolean checkCache(HttpRequest request, ChannelHandlerContext context, long lastModified) {
    String ifModifiedSince = request.getHeader(IF_MODIFIED_SINCE);
    if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
      try {
        if (Responses.DATE_FORMAT.get().parse(ifModifiedSince).getTime() >= lastModified) {
          HttpResponse response = new DefaultHttpResponse(HTTP_1_1, NOT_MODIFIED);
          addAllowAnyOrigin(response);
          addDate(response);
          addServer(response);
          send(response, request, context);
          return true;
        }
      }
      catch (ParseException ignored) {
      }
      catch (NumberFormatException ignored) {
      }
    }
    return false;
  }

  public static void sendFile(HttpRequest request, ChannelHandlerContext context, File file) throws IOException {
    if (checkCache(request, context, file.lastModified())) {
      return;
    }

    boolean fileWillBeClosed = false;
    RandomAccessFile raf = new RandomAccessFile(file, "r");
    try {
      long fileLength = raf.length();
      HttpResponse response = createResponse(file.getPath());
      addCommonHeaders(response);
      response.setHeader(LAST_MODIFIED, Responses.DATE_FORMAT.get().format(new Date(file.lastModified())));
      boolean keepAlive = addKeepAliveIfNeed(response, request);
      if (request.getMethod() != HttpMethod.HEAD) {
        setContentLength(response, fileLength);
      }

      Channel channel = context.getChannel();
      ChannelFuture future = channel.write(response);

      if (request.getMethod() != HttpMethod.HEAD) {
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
      }

      if (!keepAlive) {
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
}
