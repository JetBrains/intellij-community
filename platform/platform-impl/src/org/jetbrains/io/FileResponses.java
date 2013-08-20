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


import com.intellij.openapi.util.text.StringUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.util.Date;

import static org.jetbrains.io.Responses.*;

public class FileResponses {
  private static final MimetypesFileTypeMap FILE_MIMETYPE_MAP = new MimetypesFileTypeMap();

  public static String getContentType(String path) {
    return FILE_MIMETYPE_MAP.getContentType(path);
  }

  private static boolean checkCache(HttpRequest request, Channel channel, long lastModified) {
    String ifModifiedSince = request.headers().get(HttpHeaders.Names.IF_MODIFIED_SINCE);
    if (!StringUtil.isEmpty(ifModifiedSince)) {
      try {
        if (Responses.DATE_FORMAT.get().parse(ifModifiedSince).getTime() >= lastModified) {
          sendStatus(HttpResponseStatus.NOT_MODIFIED, channel, request);
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

  public static void sendFile(HttpRequest request, Channel channel, File file) throws IOException {
    if (checkCache(request, channel, file.lastModified())) {
      return;
    }

    boolean fileWillBeClosed = false;
    RandomAccessFile raf = new RandomAccessFile(file, "r");
    try {
      long fileLength = raf.length();
      HttpResponse response = create(getContentType(file.getPath()));
      addCommonHeaders(response);
      response.headers().set(HttpHeaders.Names.CACHE_CONTROL, "private, must-revalidate");
      response.headers().set(HttpHeaders.Names.LAST_MODIFIED, Responses.DATE_FORMAT.get().format(new Date(file.lastModified())));
      boolean keepAlive = addKeepAliveIfNeed(response, request);
      if (request.getMethod() != HttpMethod.HEAD) {
        HttpHeaders.setContentLength(response, fileLength);
      }

      ChannelFuture future = channel.write(response);
      if (request.getMethod() != HttpMethod.HEAD) {
        if (channel.pipeline().get(SslHandler.class) == null) {
          // No encryption - use zero-copy
          future = channel.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength));
        }
        else {
          // Cannot use zero-copy with HTTPS
          future = channel.write(new ChunkedFile(raf, 0, fileLength, 8192));
        }
      }

      if (!keepAlive) {
        future.addListener(ChannelFutureListener.CLOSE);
      }
      channel.flush();

      fileWillBeClosed = true;
    }
    finally {
      if (!fileWillBeClosed) {
        raf.close();
      }
    }
  }
}