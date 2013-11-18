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

import com.intellij.openapi.diagnostic.Logger;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.ConnectException;

@ChannelHandler.Sharable
public final class ChannelExceptionHandler extends ChannelInboundHandlerAdapter {
  private static final Logger LOG = Logger.getInstance(ChannelExceptionHandler.class);

  private static final ChannelInboundHandler INSTANCE = new ChannelExceptionHandler();

  private ChannelExceptionHandler() {
  }

  public static ChannelInboundHandler getInstance() {
    return INSTANCE;
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
    // don't report about errors while connecting
    // WEB-7727
    if (cause instanceof ConnectException) {
      LOG.debug(cause);
    }
    else {
      NettyUtil.log(cause, LOG);
    }
  }
}