package org.jetbrains.io;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

abstract class DelegatingHttpRequestHandlerBase extends SimpleChannelUpstreamHandler {
  @Override
  public final void messageReceived(ChannelHandlerContext context, MessageEvent event) throws Exception {
    if (!(event.getMessage() instanceof HttpRequest)) {
      context.sendUpstream(event);
      return;
    }

    HttpRequest request = (HttpRequest)event.getMessage();
    //if (BuiltInServer.LOG.isDebugEnabled()) {
    //BuiltInServer.LOG.debug(request.toString());
    //}

    if (!process(context, request, new QueryStringDecoder(request.getUri()))) {
      Responses.sendStatus(request, context, NOT_FOUND);
    }
  }

  protected abstract boolean process(ChannelHandlerContext context, HttpRequest request, QueryStringDecoder urlDecoder) throws Exception;

  @Override
  public final void exceptionCaught(ChannelHandlerContext context, ExceptionEvent event) throws Exception {
    try {
      BuiltInServer.LOG.error(event.getCause());
    }
    finally {
      context.setAttachment(null);
      event.getChannel().close();
    }
  }
}