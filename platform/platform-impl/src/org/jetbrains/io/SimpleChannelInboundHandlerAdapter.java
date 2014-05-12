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