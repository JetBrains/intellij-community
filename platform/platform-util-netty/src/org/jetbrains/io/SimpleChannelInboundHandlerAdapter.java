// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * You should use this class instead of {@link SimpleChannelInboundHandler} to simplify migration to netty 5 in the future.
 */
public abstract class SimpleChannelInboundHandlerAdapter<I> extends SimpleChannelInboundHandler<I> {
  protected SimpleChannelInboundHandlerAdapter() {
    super(true);
  }

  protected SimpleChannelInboundHandlerAdapter(boolean autoRelease) {
    super(autoRelease);
  }

  @Override
  protected final void channelRead0(ChannelHandlerContext ctx, I message) throws Exception {
    messageReceived(ctx, message);
  }

  protected abstract void messageReceived(ChannelHandlerContext context, I message) throws Exception;
}