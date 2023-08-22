// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * See [Remote Communication](https://youtrack.jetbrains.com/articles/IDEA-A-63/Remote-Communication)
 */
public abstract class BinaryRequestHandler {
  public static final ExtensionPointName<BinaryRequestHandler> EP_NAME = new ExtensionPointName<>("org.jetbrains.binaryRequestHandler");

  /**
   * uuidgen on Mac OS X could be used to generate UUID
   */
  public abstract @NotNull UUID getId();

  public abstract @NotNull ChannelHandler getInboundHandler(@NotNull ChannelHandlerContext context);
}