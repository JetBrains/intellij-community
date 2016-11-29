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
import com.intellij.util.containers.ContainerUtil;
import io.netty.channel.*;
import io.netty.util.concurrent.GenericFutureListener;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@ChannelHandler.Sharable
public final class ChannelRegistrar extends ChannelInboundHandlerAdapter {
  private static final Logger LOG = Logger.getInstance(ChannelRegistrar.class);

  private final AtomicReference<ServerChannel> serverChannel = new AtomicReference<>();
  private final Set<Channel> clientChannels = ContainerUtil.newConcurrentSet();

  private boolean isEventLoopGroupOwner;

  public boolean isEmpty() {
    return serverChannel.get() == null && clientChannels.isEmpty();
  }

  public void setServerChannel(@NotNull Channel channel, boolean isOwnEventLoopGroup) {
    boolean isSet = serverChannel.compareAndSet(null, (ServerChannel)channel);
    LOG.assertTrue(isSet);

    this.isEventLoopGroupOwner = isOwnEventLoopGroup;
  }

  @Override
  public void channelActive(@NotNull ChannelHandlerContext context) throws Exception {
    clientChannels.add(context.channel());

    super.channelActive(context);
  }

  @Override
  public void channelInactive(@NotNull ChannelHandlerContext context) throws Exception {
    clientChannels.remove(context.channel());

    super.channelInactive(context);
  }

  public void close() {
    close(isEventLoopGroupOwner);
  }

  private void close(boolean shutdownEventLoopGroup) {
    ServerChannel serverChannel = this.serverChannel.get();
    if (serverChannel == null) {
      LOG.assertTrue(clientChannels.isEmpty());
      return;
    }
    else if (!this.serverChannel.compareAndSet(serverChannel, null)) {
      return;
    }

    EventLoopGroup eventLoopGroup = shutdownEventLoopGroup ? serverChannel.eventLoop().parent() : null;
    try {
      long start = System.currentTimeMillis();
      Channel[] clientChannels = this.clientChannels.toArray(new Channel[]{});
      this.clientChannels.clear();

      final CountDownLatch countDown = new CountDownLatch(clientChannels.length + 1);
      GenericFutureListener<ChannelFuture> listener = new GenericFutureListener<ChannelFuture>() {
        @Override
        public void operationComplete(@NotNull ChannelFuture future) throws Exception {
          try {
            Throwable cause = future.cause();
            if (cause != null) {
              LOG.warn(cause);
            }
          }
          finally {
            countDown.countDown();
          }
        }
      };
      serverChannel.close().addListener(listener);
      for (Channel channel : clientChannels) {
        channel.close().addListener(listener);
      }

      try {
        countDown.await(5, TimeUnit.SECONDS);
      }
      catch (InterruptedException e) {
        LOG.warn("Cannot close all channels for 10 seconds, channels: " + Arrays.toString(clientChannels));
      }

      long duration = System.currentTimeMillis() - start;
      if (duration > 1000) {
        LOG.info("Close all channels took " + duration + " ms: " + (duration / 60000) + " min " + ((duration % 60000) / 1000) + "sec");
      }
    }
    finally {
      if (eventLoopGroup != null) {
        eventLoopGroup.shutdownGracefully(1, 2, TimeUnit.NANOSECONDS);
      }
    }
  }
}
