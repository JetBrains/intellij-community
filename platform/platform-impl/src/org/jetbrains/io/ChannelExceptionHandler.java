package org.jetbrains.io;

import com.intellij.openapi.diagnostic.Logger;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.ExceptionEvent;

import java.net.ConnectException;

public final class ChannelExceptionHandler implements ChannelUpstreamHandler {
  private static final Logger LOG = Logger.getInstance(ChannelExceptionHandler.class);

  @Override
  public void handleUpstream(ChannelHandlerContext context, ChannelEvent event) throws Exception {
    if (!(event instanceof ExceptionEvent)) {
      context.sendUpstream(event);
      return;
    }

    Throwable cause = ((ExceptionEvent)event).getCause();
    // don't report about errors while connecting
    // WEB-7727
    if (!(cause instanceof ConnectException) && !"Connection reset".equals(cause.getMessage())) {
      NettyUtil.log(cause, LOG);
    }
    else {
      LOG.debug(cause);
    }
  }
}