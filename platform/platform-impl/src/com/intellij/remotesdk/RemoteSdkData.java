package com.intellij.remotesdk;

import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public interface RemoteSdkData {
  String getInterpreterPath();

  void setInterpreterPath(String interpreterPath);

  String getFullInterpreterPath();

  String getHelpersPath();

  void setHelpersPath(String tempFilesPath);

  String getDefaultHelpersName();

  String getHost();

  void setHost(String host);

  int getPort();

  void setPort(int port);

  @Transient
  String getUserName();

  void setUserName(String userName);

  String getPassword();

  void setPassword(@Nullable String password);

  void setStorePassword(boolean storePassword);

  void setStorePassphrase(boolean storePassphrase);

  boolean isStorePassword();

  boolean isStorePassphrase();

  boolean isAnonymous();

  void setAnonymous(boolean anonymous);

  String getPrivateKeyFile();

  void setPrivateKeyFile(String privateKeyFile);

  String getKnownHostsFile();

  void setKnownHostsFile(String knownHostsFile);

  @Transient
  String getPassphrase();

  void setPassphrase(@Nullable String passphrase);

  boolean isUseKeyPair();

  void setUseKeyPair(boolean useKeyPair);

  void addRemoteRoot(String remoteRoot);

  void clearRemoteRoots();

  List<String> getRemoteRoots();

  void setRemoteRoots(List<String> remoteRoots);

  boolean isHelpersVersionChecked();

  void setHelpersVersionChecked(boolean helpersVersionChecked);
}
