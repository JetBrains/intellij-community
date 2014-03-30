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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

abstract class DelegatingHttpRequestHandlerBase extends SimpleChannelInboundHandler<FullHttpRequest> {
  @Override
  protected void messageReceived(ChannelHandlerContext context, FullHttpRequest message) throws Exception {
    if (BuiltInServer.LOG.isDebugEnabled()) {
//      BuiltInServer.LOG.debug("IN HTTP:\n" + message);
      BuiltInServer.LOG.debug("IN HTTP: " + message.getUri());
    }

    if (!process(context, message, new QueryStringDecoder(message.getUri()))) {
      Responses.sendStatus(HttpResponseStatus.NOT_FOUND, context.channel(), message);
    }
  }

  protected abstract boolean process(ChannelHandlerContext context, FullHttpRequest request, QueryStringDecoder urlDecoder) throws Exception;

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
    try {
      NettyUtil.log(cause, BuiltInServer.LOG);
    }
    finally {
      context.channel().close();
    }
  }
}