package com.intellij.remotesdk;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public interface RemoteSdkData extends MutableRemoteCredentials {
  String getInterpreterPath();

  void setInterpreterPath(String interpreterPath);

  String getFullInterpreterPath();

  String getHelpersPath();

  void setHelpersPath(String tempFilesPath);

  String getDefaultHelpersName();

  void addRemoteRoot(String remoteRoot);

  void clearRemoteRoots();

  List<String> getRemoteRoots();

  void setRemoteRoots(List<String> remoteRoots);

  boolean isHelpersVersionChecked();

  void setHelpersVersionChecked(boolean helpersVersionChecked);

  boolean isInitialized();

  void setInitialized(boolean initialized);
}
