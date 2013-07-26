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
import org.apache.sanselan.ImageWriteException;
import org.apache.sanselan.Sanselan;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.ide.HttpRequestHandler;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

@ChannelHandler.Sharable
final class DelegatingHttpRequestHandler extends DelegatingHttpRequestHandlerBase {
  @Override
  protected boolean process(ChannelHandlerContext context, HttpRequest request, QueryStringDecoder urlDecoder) throws IOException, ImageWriteException {
    HttpRequestHandler connectedHandler = (HttpRequestHandler)context.getAttachment();
    if (connectedHandler != null) {
      if (connectedHandler.isSupported(request) && connectedHandler.process(urlDecoder, request, context)) {
        return true;
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
        return true;
      }
    }

    for (HttpRequestHandler handler : BuiltInServerManager.EP_NAME.getExtensions()) {
      try {
        if (handler.isSupported(request) && handler.process(urlDecoder, request, context)) {
          if (context.getAttachment() == null) {
            context.setAttachment(handler);
          }
          return true;
        }
      }
      catch (Throwable e) {
        BuiltInServer.LOG.error(e);
      }
    }
    return false;
  }
}
