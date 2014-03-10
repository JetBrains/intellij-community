package com.intellij.remote;

/**
 * @author traff
 */
public enum RemoteConnectionType {
  DEFAULT_SDK, DEPLOYMENT_SERVER, REMOTE_SDK, CURRENT_VAGRANT, NONE;

  public static RemoteConnectionType findByName(String name) {
    try {
      return valueOf(name);
    }
    catch (Exception e) {
      return NONE;
    }
  }
}
