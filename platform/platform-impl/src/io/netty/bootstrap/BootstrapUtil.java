package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

public final class BootstrapUtil {
  public static ChannelFuture initAndRegister(Channel channel, Bootstrap bootstrap) throws Throwable {
    try {
      bootstrap.init(channel);
    }
    catch (Throwable e) {
      channel.unsafe().closeForcibly();
      throw e;
    }

    ChannelFuture regFuture = bootstrap.group().register(channel);
    //noinspection ThrowableResultOfMethodCallIgnored
    if (regFuture.cause() != null) {
      if (channel.isRegistered()) {
        channel.close();
      }
      else {
        channel.unsafe().closeForcibly();
      }
    }
    return regFuture;
  }
}
