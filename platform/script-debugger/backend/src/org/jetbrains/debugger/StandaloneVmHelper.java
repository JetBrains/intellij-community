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
package org.jetbrains.debugger;

import com.intellij.util.Consumer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.channel.oio.OioEventLoopGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.jsonProtocol.Request;
import org.jetbrains.rpc.MessageProcessor;
import org.jetbrains.rpc.MessageWriter;

import java.util.concurrent.TimeUnit;

public class StandaloneVmHelper extends MessageWriter implements Vm.AttachStateManager {
  private volatile Channel channel;

  private final VmEx vm;

  public StandaloneVmHelper(@NotNull VmEx vm) {
    this.vm = vm;
  }

  @Override
  public boolean write(@NotNull ByteBuf content) {
    return write(((Object)content));
  }

  @Nullable
  public Channel getChannelIfActive() {
    Channel currentChannel = channel;
    return currentChannel == null || !currentChannel.isActive() ? null : currentChannel;
  }

  public boolean write(@NotNull Object content) {
    Channel channel = getChannelIfActive();
    return channel != null && !channel.writeAndFlush(content).isCancelled();
  }

  public interface VmEx extends Vm {
    MessageProcessor getCommandProcessor();

    @Nullable
    Request createDisconnectRequest();
  }

  public void setChannel(@NotNull Channel channel) {
    this.channel = channel;
    channel.closeFuture().addListener(new MyChannelFutureListener());
  }

  private class MyChannelFutureListener implements ChannelFutureListener {
    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
      // don't report in case of explicit detach()
      if (channel != null) {
        vm.getCommandProcessor().closed();
        vm.getDebugListener().disconnected();
      }
    }
  }

  @Override
  public boolean isAttached() {
    return channel != null;
  }

  @Override
  @NotNull
  public Promise<Void> detach() {
    final Channel currentChannel = channel;
    if (currentChannel == null) {
      return Promise.DONE;
    }

    vm.getCommandProcessor().cancelWaitingRequests();
    Request disconnectRequest = vm.createDisconnectRequest();

    final AsyncPromise<Void> promise = new AsyncPromise<Void>();
    if (disconnectRequest == null) {
      vm.getCommandProcessor().closed();
      channel = null;
      closeChannel(currentChannel, promise);
      return promise;
    }

    vm.getCommandProcessor().closed();
    channel = null;
    //noinspection unchecked
    vm.getCommandProcessor().send(disconnectRequest).processed(new Consumer<Void>() {
      @Override
      public void consume(Void o) {
        try {
          vm.getCommandProcessor().cancelWaitingRequests();
          closeChannel(currentChannel, promise);
        }
        catch (Throwable e) {
          promise.setError(e);
        }
      }
    });
    return promise;
  }

  protected void closeChannel(@NotNull Channel channel, @NotNull final AsyncPromise<Void> promise) {
    doCloseChannel(channel, promise);
  }

  public static void doCloseChannel(@NotNull Channel channel, @NotNull final AsyncPromise<Void> promise) {
    final EventLoop eventLoop = channel.eventLoop();
    channel.close().addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        try {
          // if NIO, so, it is shared and we don't need to release it
          if (eventLoop instanceof OioEventLoopGroup) {
            eventLoop.shutdownGracefully(1, 2, TimeUnit.NANOSECONDS);
          }
        }
        finally {
          Throwable error = future.cause();
          if (error == null) {
            promise.setResult(null);
          }
          else {
            promise.setError(error);
          }
        }
      }
    });
  }
}