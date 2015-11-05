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

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
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

  @Override
  protected boolean process(@NotNull ChannelHandlerContext context,
                            @NotNull FullHttpRequest request,
                            @NotNull QueryStringDecoder urlDecoder) throws IOException, ImageWriteException {
    Attribute<HttpRequestHandler> prevHandlerAttribute = context.attr(PREV_HANDLER);
    HttpRequestHandler connectedHandler = prevHandlerAttribute.get();
    if (connectedHandler != null) {
      if (connectedHandler.isSupported(request) && connectedHandler.process(urlDecoder, request, context)) {
        return true;
      }
      // prev cached connectedHandler is not suitable for this request, so, let's find it again
      prevHandlerAttribute.set(null);
    }

    for (HttpRequestHandler handler : HttpRequestHandler.EP_NAME.getExtensions()) {
      try {
        if (handler.isSupported(request) && handler.process(urlDecoder, request, context)) {
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