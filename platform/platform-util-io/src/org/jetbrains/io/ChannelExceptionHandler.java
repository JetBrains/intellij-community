// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io;

import com.intellij.openapi.diagnostic.Logger;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;

@ChannelHandler.Sharable
public final class ChannelExceptionHandler extends ChannelHandlerAdapter {
  private static final Logger LOG = Logger.getInstance(ChannelExceptionHandler.class);

  private static final ChannelHandler INSTANCE = new ChannelExceptionHandler();

  private ChannelExceptionHandler() {
  }

  @NotNull
  public static ChannelHandler getInstance() {
    return INSTANCE;
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
    NettyUtil.logAndClose(cause, LOG, context.channel());
  }
}