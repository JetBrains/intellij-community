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
import com.intellij.util.Consumer;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public final class NettyUtil {
  public static final int DEFAULT_CONNECT_ATTEMPT_COUNT = 8;

  public static void log(Throwable throwable, Logger log) {
    if (isAsWarning(throwable)) {
      log.warn(throwable);
    }
    else {
      log.error(throwable);
    }
  }

  public static void connectClient(final ClientBootstrap bootstrap, final SocketAddress remoteAddress, final Consumer<Channel> consumer, final ActionCallback asyncResult) {
    connect(bootstrap, remoteAddress, consumer, asyncResult, DEFAULT_CONNECT_ATTEMPT_COUNT);
  }

  public static void connect(final ClientBootstrap bootstrap, final SocketAddress remoteAddress, final Consumer<Channel> consumer, final ActionCallback asyncResult, final int attemptCount) {
    final AtomicInteger attemptCounter = new AtomicInteger(1);
    bootstrap.connect(remoteAddress).addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
          consumer.consume(future.getChannel());
        }
        else if (attemptCounter.incrementAndGet() > attemptCount) {
          asyncResult.reject("cannot connect");
        }
        else if (!asyncResult.isRejected()) {
          Thread.sleep(300);
          bootstrap.connect(remoteAddress).addListener(this);
        }
      }
    });
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