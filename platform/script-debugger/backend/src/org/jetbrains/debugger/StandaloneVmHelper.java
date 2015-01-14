package org.jetbrains.debugger;

import com.intellij.util.Consumer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.io.NettyUtil;
import org.jetbrains.jsonProtocol.Request;
import org.jetbrains.rpc.MessageProcessor;
import org.jetbrains.rpc.MessageWriter;

public class StandaloneVmHelper extends MessageWriter implements Vm.AttachStateManager {
  private volatile Channel channel;

  private final VmEx vm;

  public StandaloneVmHelper(@NotNull VmEx vm) {
    this.vm = vm;
  }

  @Override
  public boolean write(@NotNull CharSequence content) {
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

    if (disconnectRequest == null) {
      vm.getCommandProcessor().closed();
      channel = null;
      return closeChannel(currentChannel);
    }

    @SuppressWarnings("unchecked")
    Promise<Void> promise = vm.getCommandProcessor().send(disconnectRequest);
    vm.getCommandProcessor().closed();
    channel = null;
    final AsyncPromise<Void> subCallback = new AsyncPromise<Void>();
    promise.processed(new Consumer<Void>() {
      @Override
      public void consume(Void o) {
        try {
          vm.getCommandProcessor().cancelWaitingRequests();
          NettyUtil.closeAndReleaseFactory(currentChannel);
        }
        finally {
          subCallback.setResult(null);
        }
      }
    });
    return subCallback;
  }

  @NotNull
  protected Promise<Void> closeChannel(@NotNull Channel channel) {
    NettyUtil.closeAndReleaseFactory(channel);
    return Promise.DONE;
  }
}