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