package com.intellij.remote;

import com.intellij.openapi.util.Pair;
import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public interface RemoteProcessHandlerBase {
  @NotNull
  PathMappingSettings getMappingSettings();

  Pair<String, Integer> obtainRemoteSocket() throws RemoteSdkException;

  void addRemoteForwarding(int remotePort, int localPort);

  List<PathMappingSettings.PathMapping> getFileMappings();
}
