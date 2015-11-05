package com.intellij.remote;

import com.google.common.net.HostAndPort;
import com.intellij.openapi.util.Pair;
import com.intellij.util.PathMapper;
import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public interface RemoteProcessControl {
  @NotNull
  PathMapper getMappingSettings();

  /**
   * @deprecated use {@link #getRemoteSocket(int)}
   */
  @Deprecated
  void addRemoteForwarding(int remotePort, int localPort);

  Pair<String, Integer> getRemoteSocket(int localPort) throws RemoteSdkException;

  @Nullable
  HostAndPort getLocalTunnel(int remotePort);

  List<PathMappingSettings.PathMapping> getFileMappings();
}
