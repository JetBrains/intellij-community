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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.text.StringUtil;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.util.CharsetUtil;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public final class Responses {
  static final ThreadLocal<DateFormat> DATE_FORMAT = new ThreadLocal<DateFormat>() {
    @Override
    protected DateFormat initialValue() {
      //noinspection SpellCheckingInspection
      SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
      format.setTimeZone(TimeZone.getTimeZone("GMT"));
      return format;
    }
  };

  private static String SERVER_HEADER_VALUE;

  public static void addAllowAnyOrigin(HttpResponse response) {
    response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
  }

  public static void addDate(HttpResponse response) {
    addDate(response, Calendar.getInstance().getTime());
  }

  public static void addDate(HttpResponse response, Date date) {
    response.setHeader(DATE, DATE_FORMAT.get().format(date));
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

  public static void addServer(HttpResponse response) {
    if (getServerHeaderValue() != null) {
      response.setHeader(SERVER, getServerHeaderValue());
    }
  }

  public static void send(String contentType, CharSequence content, HttpRequest request, ChannelHandlerContext context) {
    HttpResponse response = create(contentType);
    response.setContent(ChannelBuffers.copiedBuffer(content, CharsetUtil.UTF_8));
    send(response, request, context);
  }

  public static void send(HttpResponse response, @Nullable HttpRequest request, ChannelHandlerContext context) {
    send(response, context.getChannel(), request);
  }

  public static void send(HttpResponse response, Channel channel, @Nullable HttpRequest request) {
    ChannelBuffer content = response.getContent();
    setContentLength(response, content == ChannelBuffers.EMPTY_BUFFER ? 0 : content.readableBytes());

    boolean keepAlive = request != null && addKeepAliveIfNeed(response, request);
    addCommonHeaders(response);
    send(response, channel, !keepAlive);
  }

  public static boolean addKeepAliveIfNeed(HttpResponse response, HttpRequest request) {
    if (isKeepAlive(request)) {
      response.setHeader(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
      return true;
    }
    return false;
  }

  public static void addCommonHeaders(HttpResponse response) {
    addServer(response);
    addDate(response);
    addAllowAnyOrigin(response);
  }

  public static HttpResponse create(String contentType) {
    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
    response.setHeader(CONTENT_TYPE, contentType);
    return response;
  }

  public static void send(CharSequence content, HttpRequest request, ChannelHandlerContext context) {
    send(content, CharsetUtil.US_ASCII, request, context);
  }

  public static void send(CharSequence content, Charset charset, HttpRequest request, ChannelHandlerContext context) {
    DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
    response.setContent(ChannelBuffers.copiedBuffer(content, charset));
    send(response, request, context);
  }

  public static void send(byte[] bytes, HttpResponse response, HttpRequest request, ChannelHandlerContext context) {
    response.setContent(ChannelBuffers.wrappedBuffer(bytes));
    send(response, request, context);
  }

  public static void send(HttpResponse response, ChannelHandlerContext context) {
    send(response, context.getChannel(), true);
  }

  public static void send(HttpResponseStatus status, ChannelHandlerContext context) {
    send(new DefaultHttpResponse(HTTP_1_1, status), context);
  }

  private static void send(HttpResponse response, Channel channel, boolean close) {
    if (!channel.isOpen()) {
      return;
    }

    ChannelFuture future = channel.write(response);
    if (close) {
      future.addListener(ChannelFutureListener.CLOSE);
    }
  }

  public static void sendStatus(HttpRequest request, ChannelHandlerContext context, HttpResponseStatus responseStatus) {
    sendStatus(request, context, responseStatus, null);
  }

  public static void sendStatus(HttpRequest request, ChannelHandlerContext context, HttpResponseStatus responseStatus, @Nullable String description) {
    sendStatus(new DefaultHttpResponse(HTTP_1_1, responseStatus), request, context.getChannel(), description);
  }

  public static void sendStatus(HttpResponse response, HttpRequest request, ChannelHandlerContext context) {
    sendStatus(response, request, context.getChannel(), null);
  }

  public static void sendStatus(HttpResponseStatus responseStatus, Channel channel) {
    sendStatus(new DefaultHttpResponse(HTTP_1_1, responseStatus), null, channel, null);
  }

  private static void sendStatus(HttpResponse response, @Nullable HttpRequest request, Channel channel, @Nullable String description) {
    response.setHeader(CONTENT_TYPE, "text/html");
    if (request == null || request.getMethod() != HttpMethod.HEAD) {
      String message = response.getStatus().toString();

      StringBuilder builder = new StringBuilder();
      builder.append("<!doctype html><title>").append(message).append("</title>").append("<h1 style=\"text-align: center\">").append(message).append("</h1>");
      if (description != null) {
        builder.append("<p>").append(description).append("</p>");
      }
      builder.append("<hr/><p style=\"text-align: center\">").append(StringUtil.notNullize(getServerHeaderValue(), "")).append("</p>");

      response.setContent(ChannelBuffers.copiedBuffer(builder, CharsetUtil.UTF_8));
    }
    send(response, channel, request);
  }

  public static void sendOptionsResponse(String allowHeaders, HttpRequest request, ChannelHandlerContext context) {
    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
    response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    response.setHeader(ACCESS_CONTROL_ALLOW_METHODS, allowHeaders);
    response.setHeader(ALLOW, allowHeaders);
    send(response, request, context);
  }
}
