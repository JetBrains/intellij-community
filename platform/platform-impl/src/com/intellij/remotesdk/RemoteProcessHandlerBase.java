package com.intellij.remotesdk;

import com.intellij.openapi.util.Pair;
import com.intellij.util.PathMappingSettings;

import java.util.List;

/**
 * @author traff
 */
public interface RemoteProcessHandlerBase {
  PathMappingSettings getMappingSettings();

  Pair<String, Integer> obtainRemoteSocket() throws RemoteInterpreterException;

  void addRemoteForwarding(int remotePort, int localPort);

  List<PathMappingSettings.PathMapping> getFileMappings();
}
