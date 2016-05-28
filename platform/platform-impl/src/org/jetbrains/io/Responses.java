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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.text.StringUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Calendar;

public final class Responses {
  private static String SERVER_HEADER_VALUE;

  public static FullHttpResponse response(HttpResponseStatus status) {
    return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.EMPTY_BUFFER);
  }

  @NotNull
  public static FullHttpResponse response(@Nullable String contentType, @Nullable ByteBuf content) {
    FullHttpResponse response = content == null
                                ? new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                                : new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
    if (contentType != null) {
      response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
    }
    return response;
  }

  public static void setDate(@NotNull HttpResponse response) {
    if (!response.headers().contains(HttpHeaderNames.DATE)) {
      response.headers().set(HttpHeaderNames.DATE, Calendar.getInstance().getTime());
    }
  }

  public static void addNoCache(@NotNull HttpResponse response) {
    response.headers().add(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate, max-age=0");
    response.headers().add(HttpHeaderNames.PRAGMA, "no-cache");
  }

  @Nullable
  public static String getServerHeaderValue() {
    if (SERVER_HEADER_VALUE == null) {
      Application app = ApplicationManager.getApplication();
      if (app != null && !app.isDisposed()) {
        SERVER_HEADER_VALUE = ApplicationInfoEx.getInstanceEx().getFullApplicationName();
      }
    }
    return SERVER_HEADER_VALUE;
  }

  public static void addServer(@NotNull HttpResponse response) {
    if (getServerHeaderValue() != null) {
      response.headers().add(HttpHeaderNames.SERVER, getServerHeaderValue());
    }
  }

  public static void send(@NotNull HttpResponse response, Channel channel, @Nullable HttpRequest request) {
    if (response.status() != HttpResponseStatus.NOT_MODIFIED && !HttpUtil.isContentLengthSet(response)) {
      HttpUtil.setContentLength(response,
                                   response instanceof FullHttpResponse ? ((FullHttpResponse)response).content().readableBytes() : 0);
    }

    addCommonHeaders(response);
    send(response, channel, request != null && !addKeepAliveIfNeed(response, request));
  }

  public static boolean addKeepAliveIfNeed(HttpResponse response, HttpRequest request) {
    if (HttpUtil.isKeepAlive(request)) {
      HttpUtil.setKeepAlive(response, true);
      return true;
    }
    return false;
  }

  public static void addCommonHeaders(@NotNull HttpResponse response) {
    addServer(response);
    setDate(response);
  }

  public static void send(CharSequence content, Channel channel, @Nullable HttpRequest request) {
    send(content, CharsetUtil.US_ASCII, channel, request);
  }

  public static void send(CharSequence content, Charset charset, Channel channel, @Nullable HttpRequest request) {
    send(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(content, charset)), channel, request);
  }

  public static void send(@NotNull HttpResponse response, @NotNull Channel channel, boolean close) {
    if (!channel.isActive()) {
      return;
    }

    ChannelFuture future = channel.write(response);
    if (!(response instanceof FullHttpResponse)) {
      channel.write(LastHttpContent.EMPTY_LAST_CONTENT);
    }
    channel.flush();
    if (close) {
      future.addListener(ChannelFutureListener.CLOSE);
    }
  }

  public static void sendStatus(HttpResponseStatus responseStatus, Channel channel) {
    sendStatus(responseStatus, channel, null);
  }

  public static void sendStatus(HttpResponseStatus responseStatus, Channel channel, @Nullable HttpRequest request) {
    sendStatus(responseStatus, channel, null, request);
  }

  public static void sendStatus(@NotNull HttpResponseStatus responseStatus, Channel channel, @Nullable String description, @Nullable HttpRequest request) {
    send(createStatusResponse(responseStatus, request, description), channel, request);
  }

  private static HttpResponse createStatusResponse(HttpResponseStatus responseStatus, @Nullable HttpRequest request, @Nullable String description) {
    if (request != null && request.method() == HttpMethod.HEAD) {
      return response(responseStatus);
    }

    StringBuilder builder = new StringBuilder();
    String message = responseStatus.toString();
    builder.append("<!doctype html><title>").append(message).append("</title>").append("<h1 style=\"text-align: center\">").append(message).append("</h1>");
    if (description != null) {
      builder.append("<p>").append(description).append("</p>");
    }
    builder.append("<hr/><p style=\"text-align: center\">").append(StringUtil.notNullize(getServerHeaderValue(), "")).append("</p>");

    DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, responseStatus, ByteBufUtil.encodeString(ByteBufAllocator.DEFAULT, CharBuffer.wrap(builder), CharsetUtil.UTF_8));
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
    return response;
  }
}