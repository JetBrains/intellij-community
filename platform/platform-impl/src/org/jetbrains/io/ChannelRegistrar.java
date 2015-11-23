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

import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

  @NotNull
  public Future<?> close(boolean shutdownEventLoopGroup) {
    EventLoopGroup eventLoopGroup = null;
    if (shutdownEventLoopGroup) {
      for (Channel channel : openChannels) {
        if (channel instanceof ServerChannel) {
          eventLoopGroup = channel.eventLoop().parent();
          break;
        }
      }
    }

    Future<?> result;
    try {
      ChannelGroupFuture groupFuture = openChannels.close();
      groupFuture.awaitUninterruptibly(30, TimeUnit.SECONDS);
      result = groupFuture;
    }
    finally {
      if (eventLoopGroup != null) {
        result = eventLoopGroup.shutdownGracefully(1, 2, TimeUnit.NANOSECONDS);
      }
    }
    return result;
  }
}
