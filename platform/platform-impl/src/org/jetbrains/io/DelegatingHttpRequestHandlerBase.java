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

import com.intellij.openapi.diagnostic.Logger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;

abstract class DelegatingHttpRequestHandlerBase extends SimpleChannelInboundHandlerAdapter<FullHttpRequest> {
  @Override
  protected void messageReceived(ChannelHandlerContext context, FullHttpRequest message) throws Exception {
    if (Logger.getInstance(BuiltInServer.class).isDebugEnabled()) {
      Logger.getInstance(BuiltInServer.class).debug("IN HTTP: " + message.uri());
    }

    if (!process(context, message, new QueryStringDecoder(message.uri()))) {
      Responses.sendStatus(HttpResponseStatus.NOT_FOUND, context.channel(), message);
    }
  }

  protected abstract boolean process(@NotNull ChannelHandlerContext context,
                                     @NotNull FullHttpRequest request,
                                     @NotNull QueryStringDecoder urlDecoder) throws Exception;

  @Override
  public void exceptionCaught(@NotNull ChannelHandlerContext context, @NotNull Throwable cause) {
    NettyUtil.logAndClose(cause, Logger.getInstance(BuiltInServer.class), context.channel());
  }
}