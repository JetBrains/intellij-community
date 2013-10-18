package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;

public final class BootstrapUtil {
  public static ChannelFuture initAndRegister(Channel channel, Bootstrap bootstrap) throws Throwable {
    try {
      bootstrap.init(channel);
    }
    catch (Throwable e) {
      channel.unsafe().closeForcibly();
      throw e;
    }

    ChannelPromise regPromise = channel.newPromise();
    channel.unsafe().register(regPromise);
    //noinspection ThrowableResultOfMethodCallIgnored
    if (regPromise.cause() != null) {
      if (channel.isRegistered()) {
        channel.close();
      }
      else {
        channel.unsafe().closeForcibly();
      }
    }
    return regPromise;
  }
}
