/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.ActionCallback;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.SocketAddress;

public final class NettyUtil {
  public static final int DEFAULT_CONNECT_ATTEMPT_COUNT = 8;
  public static final int MIN_START_TIME = 100;

  public static void log(Throwable throwable, Logger log) {
    if (isAsWarning(throwable)) {
      log.warn(throwable);
    }
    else {
      log.error(throwable);
    }
  }

  public static Channel connectClient(ClientBootstrap bootstrap, SocketAddress remoteAddress, ActionCallback asyncResult) {
    return connect(bootstrap, remoteAddress, asyncResult, DEFAULT_CONNECT_ATTEMPT_COUNT);
  }

  @Nullable
  public static Channel connect(ClientBootstrap bootstrap, SocketAddress remoteAddress, ActionCallback asyncResult, int maxAttemptCount) {
    int attemptCount = 0;
    while (true) {
      try {
        ChannelFuture future = bootstrap.connect(remoteAddress).await();
        if (future.isSuccess()) {
          return future.getChannel();
        }
        else if (asyncResult.isRejected()) {
          return null;
        }
        else if (++attemptCount < maxAttemptCount) {
          //noinspection BusyWait
          Thread.sleep(attemptCount * 100);
        }
        else {
          asyncResult.reject("cannot connect");
          return null;
        }
      }
      catch (Throwable e) {
        asyncResult.reject(e.getMessage());
        return null;
      }
    }
  }

  private static boolean isAsWarning(Throwable throwable) {
    String message = throwable.getMessage();
    if (message == null) {
      return false;
    }

    return (throwable instanceof IOException && message.equals("An existing connection was forcibly closed by the remote host")) ||
           (throwable instanceof ChannelException && message.startsWith("Failed to bind to: "));
  }

  // applicable only in case of ClientBootstrap&OioClientSocketChannelFactory
  public static void closeAndReleaseFactory(Channel channel) {
    ChannelFactory channelFactory = channel.getFactory();
    try {
      channel.close().awaitUninterruptibly();
    }
    finally {
      // in our case it does nothing, we don't use ExecutorService, but we are aware of future changes
      channelFactory.releaseExternalResources();
    }
  }
}