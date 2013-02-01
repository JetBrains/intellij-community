package com.intellij.remotesdk;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public interface RemoteSdkData extends RemoteCredentials {
  String getInterpreterPath();

  void setInterpreterPath(String interpreterPath);

  String getFullInterpreterPath();

  String getHelpersPath();

  void setHelpersPath(String tempFilesPath);

  String getDefaultHelpersName();

  void setHost(String host);

  void setPort(int port);

  void setUserName(String userName);

  void setPassword(@Nullable String password);

  void setStorePassword(boolean storePassword);

  void setStorePassphrase(boolean storePassphrase);

  void setAnonymous(boolean anonymous);

  void setPrivateKeyFile(String privateKeyFile);

  void setKnownHostsFile(String knownHostsFile);

  void setPassphrase(@Nullable String passphrase);

  void setUseKeyPair(boolean useKeyPair);

  void addRemoteRoot(String remoteRoot);

  void clearRemoteRoots();

  List<String> getRemoteRoots();

  void setRemoteRoots(List<String> remoteRoots);

  boolean isHelpersVersionChecked();

  void setHelpersVersionChecked(boolean helpersVersionChecked);

  boolean isInitialized();

  void setInitialized(boolean initialized);
}
