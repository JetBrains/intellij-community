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
      NettyUtil.log(event.getCause(), BuiltInServer.LOG);
    }
    finally {
      context.setAttachment(null);
      event.getChannel().close();
    }
  }
}