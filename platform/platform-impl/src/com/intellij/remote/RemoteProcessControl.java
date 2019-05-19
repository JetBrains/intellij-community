package com.intellij.remote;

import com.google.common.net.HostAndPort;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public interface RemoteProcessControl extends ProcessControlWithMappings {

  /**
   * @deprecated use {@link #getRemoteSocket(int)}
   */
  @Deprecated
  void addRemoteForwarding(int remotePort, int localPort);

  Pair<String, Integer> getRemoteSocket(int localPort) throws RemoteSdkException;

  @Nullable
  HostAndPort getLocalTunnel(int remotePort);
}
