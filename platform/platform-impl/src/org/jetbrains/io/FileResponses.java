/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;

import static org.jetbrains.io.Responses.*;

public class FileResponses {
  private static final MimetypesFileTypeMap FILE_MIMETYPE_MAP = new MimetypesFileTypeMap();

  public static String getContentType(String path) {
    return FILE_MIMETYPE_MAP.getContentType(path);
  }

  private static boolean checkCache(@NotNull HttpRequest request, @NotNull Channel channel, long lastModified) {
    Long ifModified = request.headers().getTimeMillis(HttpHeaderNames.IF_MODIFIED_SINCE);
    if (ifModified != null && ifModified >= lastModified) {
      send(response(HttpResponseStatus.NOT_MODIFIED), channel, request);
      return true;
    }
    return false;
  }

  @Nullable
  public static HttpResponse prepareSend(@NotNull HttpRequest request, @NotNull Channel channel, long lastModified, @NotNull String path) {
    if (checkCache(request, channel, lastModified)) {
      return null;
    }

    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, getContentType(path));
    addCommonHeaders(response);
    response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, must-revalidate");
    response.headers().set(HttpHeaderNames.LAST_MODIFIED, new Date(lastModified));
    return response;
  }

  public static void sendFile(@NotNull HttpRequest request, @NotNull Channel channel, @NotNull File file) throws IOException {
    HttpResponse response = prepareSend(request, channel, file.lastModified(), file.getPath());
    if (response == null) {
      return;
    }

    boolean keepAlive = addKeepAliveIfNeed(response, request);

    boolean fileWillBeClosed = false;
    RandomAccessFile raf;
    try {
      raf = new RandomAccessFile(file, "r");
    }
    catch (FileNotFoundException ignored) {
      send(response(HttpResponseStatus.NOT_FOUND), channel, request);
      return;
    }

    try {
      long fileLength = raf.length();
      HttpUtil.setContentLength(response, fileLength);

      channel.write(response);
      if (request.method() != HttpMethod.HEAD) {
        if (channel.pipeline().get(SslHandler.class) == null) {
          // no encryption - use zero-copy
          channel.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength));
        }
        else {
          // cannot use zero-copy with HTTPS
          channel.write(new ChunkedFile(raf));
        }
      }
      fileWillBeClosed = true;
    }
    finally {
      if (!fileWillBeClosed) {
        raf.close();
      }
    }

    ChannelFuture future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE);
    }
  }
}