package com.intellij.remoteServer.runtime.deployment.debug;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class JavaDebugConnectionData implements DebugConnectionData {
  private final String myHost;
  private final int myPort;

  public JavaDebugConnectionData(@NotNull String host, int port) {
    myHost = host;
    myPort = port;
  }

  @NotNull
  public String getHost() {
    return myHost;
  }

  public int getPort() {
    return myPort;
  }
}
