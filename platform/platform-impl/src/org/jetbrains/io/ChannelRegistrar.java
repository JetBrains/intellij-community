package org.jetbrains.io;

import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.jetbrains.annotations.NotNull;

@ChannelHandler.Sharable
public final class ChannelRegistrar extends ChannelInboundHandlerAdapter {
  private final ChannelGroup openChannels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);

  public boolean isEmpty() {
    return openChannels.isEmpty();
  }

  public void add(@NotNull Channel serverChannel) {
    assert serverChannel instanceof ServerChannel;
    openChannels.add(serverChannel);
  }

  @Override
  public void channelActive(ChannelHandlerContext context) throws Exception {
    // we don't need to remove channel on close - ChannelGroup do it
    openChannels.add(context.channel());

    super.channelActive(context);
  }

  public void close() {
    close(true);
  }

  public void close(boolean shutdownEventLoopGroup) {
    EventLoopGroup eventLoopGroup = null;
    if (shutdownEventLoopGroup) {
      for (Channel channel : openChannels) {
        if (channel instanceof ServerChannel) {
          eventLoopGroup = channel.eventLoop().parent();
          break;
        }
      }
    }

    try {
      openChannels.close().awaitUninterruptibly();
    }
    finally {
      if (eventLoopGroup != null) {
        eventLoopGroup.shutdownGracefully();
      }
    }
  }
}