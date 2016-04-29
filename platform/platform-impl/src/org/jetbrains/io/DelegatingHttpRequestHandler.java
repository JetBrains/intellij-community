package org.jetbrains.io;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.IconLoader;
import org.apache.sanselan.ImageFormat;
import org.apache.sanselan.Sanselan;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.HttpRequestHandler;
import org.jetbrains.ide.WebServerManager;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

@ChannelHandler.Sharable
final class DelegatingHttpRequestHandler extends SimpleChannelUpstreamHandler {
  private static boolean checkAndProcess(@NotNull HttpRequestHandler handler,
                                         @NotNull ChannelHandlerContext context,
                                         @NotNull HttpRequest request,
                                         @NotNull QueryStringDecoder urlDecoder) throws IOException {
    return handler.isSupported(request) && !NettyUtil.isWriteFromBrowserWithoutOrigin(request) && handler.isAccessible(request) && handler.process(urlDecoder, request, context);
  }

  @Override
  public void messageReceived(ChannelHandlerContext context, MessageEvent event) throws Exception {
    if (!(event.getMessage() instanceof HttpRequest)) {
      context.sendUpstream(event);
      return;
    }

    HttpRequest request = (HttpRequest)event.getMessage();
    QueryStringDecoder urlDecoder = new QueryStringDecoder(request.getUri());
    HttpRequestHandler connectedHandler = (HttpRequestHandler)context.getAttachment();
    if (connectedHandler == null) {
      if (urlDecoder.getPath().equals("/favicon.ico")) {
        Icon icon = IconLoader.findIcon(ApplicationInfoEx.getInstanceEx().getSmallIconUrl());
        if (icon != null) {
          //noinspection UndesirableClassUsage
          BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
          icon.paintIcon(null, image.getGraphics(), 0, 0);
          byte[] icoBytes = Sanselan.writeImageToBytes(image, ImageFormat.IMAGE_FORMAT_ICO, null);
          Responses.send(FileResponses.createResponse(urlDecoder.getPath()), icoBytes, request, context);
          return;
        }
      }
      else if (urlDecoder.getPath().equals(WebServer.START_TIME_PATH)) {
        Responses.send(WebServer.getApplicationStartTime(), request, context);
        return;
      }

      for (HttpRequestHandler handler : WebServerManager.EP_NAME.getExtensions()) {
        try {
          if (checkAndProcess(handler, context, request, urlDecoder)) {
            if (context.getAttachment() == null) {
              context.setAttachment(handler);
            }
            return;
          }
        }
        catch (Throwable e) {
          WebServer.LOG.error(e);
        }
      }
    }
    else {
      if (checkAndProcess(connectedHandler, context, request, urlDecoder)) {
        return;
      }
    }
    Responses.sendError(request, context, NOT_FOUND);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, ExceptionEvent event) throws Exception {
    try {
      WebServer.LOG.error(event.getCause());
    }
    finally {
      context.setAttachment(null);
      event.getChannel().close();
    }
  }
}
