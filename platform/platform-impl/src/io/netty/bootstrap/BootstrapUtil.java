package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.jetbrains.annotations.NotNull;

public final class BootstrapUtil {
  public static ChannelFuture initAndRegister(@NotNull Channel channel, @NotNull Bootstrap bootstrap) throws Throwable {
    try {
      bootstrap.init(channel);
    }
    catch (Throwable e) {
      channel.unsafe().closeForcibly();
      throw e;
    }

    ChannelFuture registrationFuture = bootstrap.group().register(channel);
    //noinspection ThrowableResultOfMethodCallIgnored
    if (registrationFuture.cause() != null) {
      if (channel.isRegistered()) {
        channel.close();
      }
      else {
        channel.unsafe().closeForcibly();
      }
    }
    return registrationFuture;
  }
}
