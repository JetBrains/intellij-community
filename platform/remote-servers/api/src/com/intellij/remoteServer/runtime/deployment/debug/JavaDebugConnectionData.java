// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.runtime.deployment.debug;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaDebugConnectionData implements DebugConnectionData {
  private final String myHost;
  private final int myPort;

  public JavaDebugConnectionData(@NotNull String host, int port) {
    myHost = host;
    myPort = port;
  }

  public @NotNull String getHost() {
    return myHost;
  }

  public int getPort() {
    return myPort;
  }

  public @Nullable JavaDebugServerModeHandler getServerModeHandler() {
    return null;
  }
}
