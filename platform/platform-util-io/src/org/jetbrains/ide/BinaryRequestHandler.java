// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public abstract class BinaryRequestHandler {
  public static final ExtensionPointName<BinaryRequestHandler> EP_NAME = ExtensionPointName.create("org.jetbrains.binaryRequestHandler");

  /**
   * uuidgen on Mac OS X could be used to generate UUID
   */
  @NotNull
  public abstract UUID getId();

  @NotNull
  public abstract ChannelHandler getInboundHandler(@NotNull ChannelHandlerContext context);
}