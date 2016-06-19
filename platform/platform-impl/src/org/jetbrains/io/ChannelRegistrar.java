/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@ChannelHandler.Sharable
public final class ChannelRegistrar extends ChannelInboundHandlerAdapter {
  private static final Logger LOG = Logger.getInstance(ChannelRegistrar.class);

  private final ChannelGroup openChannels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE, true);
  private boolean isEventLoopGroupOwner;

  public boolean isEmpty() {
    return openChannels.isEmpty();
  }

  public void add(@NotNull Channel serverChannel, boolean isOwnEventLoopGroup) {
    this.isEventLoopGroupOwner = isOwnEventLoopGroup;
    assert serverChannel instanceof ServerChannel;
    openChannels.add(serverChannel);
  }

  @Override
  public void channelActive(ChannelHandlerContext context) throws Exception {
    // we don't need to remove channel on close - ChannelGroup do it
    openChannels.add(context.channel());

    super.channelActive(context);
  }

  @NotNull
  public Future<?> close() {
    return close(isEventLoopGroupOwner);
  }

  @NotNull
  private Future<?> close(boolean shutdownEventLoopGroup) {
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
      long start = System.currentTimeMillis();
      Object[] channels = openChannels.toArray(new Channel[]{});
      ChannelGroupFuture groupFuture = openChannels.close();
      // server channels are closed in first turn, so, small timeout is relatively ok
      if (!groupFuture.awaitUninterruptibly(10, TimeUnit.SECONDS)) {
        LOG.warn("Cannot close all channels for 10 seconds, channels: " + Arrays.toString(channels));
      }
      result = groupFuture;

      long duration = System.currentTimeMillis() - start;
      if (duration > 1000) {
        LOG.info("Close all channels took " + duration + " ms: " + (duration / 60000) + " min " + ((duration % 60000) / 1000) + "sec");
      }
    }
    finally {
      if (eventLoopGroup != null) {
        result = eventLoopGroup.shutdownGracefully(1, 2, TimeUnit.NANOSECONDS);
      }
    }
    return result;
  }
}
