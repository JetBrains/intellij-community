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

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import org.apache.sanselan.ImageFormat;
import org.apache.sanselan.Sanselan;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.ide.HttpRequestHandler;
import org.jetbrains.ide.BuiltInServerManager;

import javax.swing.*;
import java.awt.image.BufferedImage;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

@ChannelHandler.Sharable
final class DelegatingHttpRequestHandler extends SimpleChannelUpstreamHandler {
  @Override
  public void messageReceived(ChannelHandlerContext context, MessageEvent event) throws Exception {
    if (!(event.getMessage() instanceof HttpRequest)) {
      context.sendUpstream(event);
      return;
    }

    HttpRequest request = (HttpRequest)event.getMessage();
    if (BuiltInServer.LOG.isDebugEnabled()) {
      BuiltInServer.LOG.debug(request.toString());
    }

    QueryStringDecoder urlDecoder = new QueryStringDecoder(request.getUri());

    HttpRequestHandler connectedHandler = (HttpRequestHandler)context.getAttachment();
    if (connectedHandler != null) {
      if (connectedHandler.isSupported(request) && connectedHandler.process(urlDecoder, request, context)) {
        return;
      }
      // prev cached connectedHandler is not suitable for this request, so, let's find it again
      context.setAttachment(null);
    }

    if (urlDecoder.getPath().equals("/favicon.ico")) {
      Icon icon = IconLoader.findIcon(ApplicationInfoEx.getInstanceEx().getSmallIconUrl());
      if (icon != null) {
        BufferedImage image = UIUtil.createImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        icon.paintIcon(null, image.getGraphics(), 0, 0);
        byte[] icoBytes = Sanselan.writeImageToBytes(image, ImageFormat.IMAGE_FORMAT_ICO, null);
        Responses.send(icoBytes, FileResponses.createResponse(urlDecoder.getPath()), request, context);
        return;
      }
    }
    else if (urlDecoder.getPath().equals(BuiltInServer.START_TIME_PATH)) {
      Responses.send(BuiltInServer.getApplicationStartTime(), request, context);
      return;
    }

    for (HttpRequestHandler handler : BuiltInServerManager.EP_NAME.getExtensions()) {
      try {
        if (handler.isSupported(request) && handler.process(urlDecoder, request, context)) {
          if (context.getAttachment() == null) {
            context.setAttachment(handler);
          }
          return;
        }
      }
      catch (Throwable e) {
        BuiltInServer.LOG.error(e);
      }
    }
    Responses.sendStatus(request, context, NOT_FOUND);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, ExceptionEvent event) throws Exception {
    try {
      BuiltInServer.LOG.error(event.getCause());
    }
    finally {
      context.setAttachment(null);
      event.getChannel().close();
    }
  }
}
