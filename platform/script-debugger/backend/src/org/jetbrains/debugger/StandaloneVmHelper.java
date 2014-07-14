package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.NettyUtil;
import org.jetbrains.jsonProtocol.Request;
import org.jetbrains.rpc.MessageHandler;
import org.jetbrains.rpc.MessageWriter;

public class StandaloneVmHelper extends MessageWriter {
  private volatile Channel channel;

  private final VmEx vm;

  public StandaloneVmHelper(@NotNull VmEx vm) {
    this.vm = vm;
  }

  @Override
  public boolean write(@NotNull CharSequence content) {
    return write(((Object)content));
  }

  public boolean write(@NotNull Object content) {
    Channel currentChannel = channel;
    if (currentChannel == null || !currentChannel.isActive()) {
      return false;
    }
    currentChannel.writeAndFlush(content);
    return true;
  }

  public interface VmEx extends Vm {
    MessageHandler<?, ?, ?, ?> getCommandProcessor();

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

  public boolean isAttached() {
    return channel != null;
  }

  @NotNull
  public ActionCallback detach() {
    final Channel currentChannel = channel;
    if (currentChannel == null) {
      return ActionCallback.DONE;
    }

    vm.getCommandProcessor().cancelWaitingRequests();
    Request disconnectRequest = vm.createDisconnectRequest();

    if (disconnectRequest == null) {
      vm.getCommandProcessor().closed();
      channel = null;
      return closeChannel(currentChannel);
    }

    ActionCallback callback = vm.getCommandProcessor().send(disconnectRequest);
    vm.getCommandProcessor().closed();
    channel = null;
    final ActionCallback subCallback = new ActionCallback();
    callback.doWhenProcessed(new Runnable() {
      @Override
      public void run() {
        try {
          vm.getCommandProcessor().cancelWaitingRequests();
          NettyUtil.closeAndReleaseFactory(currentChannel);
        }
        finally {
          subCallback.setDone();
        }
      }
    });
    return subCallback;
  }

  @NotNull
  protected ActionCallback closeChannel(@NotNull Channel channel) {
    NettyUtil.closeAndReleaseFactory(channel);
    return ActionCallback.DONE;
  }
}