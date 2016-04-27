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

import com.google.common.base.Strings;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Base64;
import com.intellij.util.BuiltinWebServerAccess;
import com.intellij.util.ui.UIUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.apache.sanselan.ImageFormat;
import org.apache.sanselan.ImageWriteException;
import org.apache.sanselan.Sanselan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.HttpRequestHandler;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

@ChannelHandler.Sharable
final class DelegatingHttpRequestHandler extends DelegatingHttpRequestHandlerBase {
  private static final AttributeKey<HttpRequestHandler> PREV_HANDLER = AttributeKey.valueOf("DelegatingHttpRequestHandler.handler");
  private static final Logger LOG = Logger.getInstance(DelegatingHttpRequestHandler.class);


  @Override
  protected boolean process(@NotNull ChannelHandlerContext context,
                            @NotNull FullHttpRequest request,
                            @NotNull QueryStringDecoder urlDecoder) throws IOException, ImageWriteException {

    // authenticateAndUpdateUrlIfNeeded enforces authentication of all requests of the webserver.
    // See the method's declaration for important details.
    if (!authenticateAndUpdateUrlIfNeeded(request)) {
      String product = ApplicationInfoEx.getInstance().getVersionName();
      Responses.sendUnauthorizedAuthenticate(context, request, product);
      return true;
    }

    Attribute<HttpRequestHandler> prevHandlerAttribute = context.attr(PREV_HANDLER);
    HttpRequestHandler connectedHandler = prevHandlerAttribute.get();
    if (connectedHandler != null) {
      if (checkAndProcess(connectedHandler, context, request, urlDecoder)) {
        return true;
      }
      // prev cached connectedHandler is not suitable for this request, so, let's find it again
      prevHandlerAttribute.set(null);
    }

    for (HttpRequestHandler handler : HttpRequestHandler.EP_NAME.getExtensions()) {
      try {
        if (checkAndProcess(handler, context, request, urlDecoder)) {
          prevHandlerAttribute.set(handler);
          return true;
        }
      }
      catch (Throwable e) {
        Logger.getInstance(BuiltInServer.class).error(e);
      }
    }

    if (urlDecoder.path().equals("/favicon.ico")) {
      Icon icon = IconLoader.findIcon(ApplicationInfoEx.getInstanceEx().getSmallIconUrl());
      if (icon != null) {
        BufferedImage image = UIUtil.createImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        icon.paintIcon(null, image.getGraphics(), 0, 0);
        byte[] icoBytes = Sanselan.writeImageToBytes(image, ImageFormat.IMAGE_FORMAT_ICO, null);
        HttpResponse response = Responses.response(FileResponses.getContentType(urlDecoder.path()), Unpooled.wrappedBuffer(icoBytes));
        Responses.addNoCache(response);
        Responses.send(response, context.channel(), request);
        return true;
      }
    }

    return false;
  }

  private boolean checkAndProcess(HttpRequestHandler connectedHandler,
                               ChannelHandlerContext context,
                               FullHttpRequest request,
                               QueryStringDecoder urlDecoder) throws IOException {
    if (connectedHandler.isSupported(request)) {
      return connectedHandler.process(urlDecoder, request, context);
    }
    return false;
  }

  /**
   * authenticateAndUpdateUrlIfNeeded provides security to the requests by enforcing authentication.
   * Authentication can be done in two ways, both ways use a token as read from {@link BuiltinWebServerAccess}:<br/>
   * <ol>
   *   <li>Basic authentication, with user: _token_ and password the token from above</li>
   *   <li>Url prefixing in the form of /&lt;token&gt;/original_url</li</>
   * </ol>
   *
   * The latter method is to support browser access as some browsers (Internet Explorer) do not support the http://user:pass@host scheme.
   * In that case we allow browser to log in with the token in the path. authenticateAndUpdateUrlIfNeeded will in that case update
   * the url in the FullHttpRequest to strip the token, so that delegated Handlers do not have to deal with the token.
   */
  private boolean authenticateAndUpdateUrlIfNeeded(@NotNull FullHttpRequest request) {
    // We always allow access to favicon.ico, avoiding the browser will prompt for authentication.
    if (request.uri().equals("/favicon.ico")) {
      return true;
    }
    String expectedToken = null;
    try {
      expectedToken = BuiltinWebServerAccess.getUserAuthenticationToken();
    }
    catch (IOException e) {
      LOG.error("Unable to read user authentication token", e);
      return false;
    }

    String receivedToken = getTokenFromAuthorization(request);
    if (receivedToken != null) {
      return expectedToken.equals(receivedToken);
    }
    return authenticateOnUrlAndUpdateUrl(request, expectedToken);
  }

  private boolean authenticateOnUrlAndUpdateUrl(@NotNull FullHttpRequest request, @NotNull String expectedToken) {
    String uri = request.uri();
    String prefix = "/" + expectedToken + "/";
    if (uri.startsWith(prefix)) {
      uri = uri.substring(prefix.length() - 1);
      request.setUri(uri);
      return true;
    }
    return false;
  }

  private String getTokenFromAuthorization(@NotNull FullHttpRequest request) {
    String authorization = request.headers().get("Authorization");
    if (Strings.isNullOrEmpty(authorization)) {
      return null;
    }
    if (!authorization.startsWith("Basic ") || authorization.length() < 7) {
      LOG.warn(String.format("Invalid authorization header '%s', unexpected type.", authorization));
      return null;
    }
    String base64 = authorization.substring(6);
    String decoded = null;
    try {
      decoded = new String(Base64.decode(base64));
    } catch (IllegalArgumentException e) {
      LOG.warn(String.format("Invalid authorization header '%s', bad base64 encoding.", authorization));
      return null;
    }
    String[] parts = decoded.split(":");
    if (parts.length != 2) {
      LOG.warn(String.format("Invalid authorization header '%s', bad username/password pair.", authorization));
      return null;
    }
    String username = parts[0];
    String password = parts[1];
    if (!"_token_".equals(username)) {
      LOG.warn(String.format("Invalid authorization header '%s'", authorization));
      return null;
    }
    return password;
  }

  @Override
  public void exceptionCaught(@NotNull ChannelHandlerContext context, @NotNull Throwable cause) {
    try {
      context.attr(PREV_HANDLER).remove();
    }
    finally {
      super.exceptionCaught(context, cause);
    }
  }
}
