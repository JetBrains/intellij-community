package com.intellij.remote;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class denotes the type of the source to obtain remote credentials. 
 * 
 * @author traff
 */
public enum RemoteConnectionType {
  /**
   * Currently selected SDK (e.g. <Project default>) 
   */
  DEFAULT_SDK,
  /**
   * Web deployment server  
   */
  DEPLOYMENT_SERVER,
  /**
   * Remote SDK
   */
  REMOTE_SDK,
  /**
   * Current project vagrant
   */
  CURRENT_VAGRANT,
  /**
   * No source is predefined - it would be asked on request 
   */
  NONE;

  private static final Logger LOG = Logger.getInstance(RemoteConnectionType.class);

  @NotNull
  public static RemoteConnectionType findByName(@Nullable String name) {
    if (name == null) {
      return NONE;
    }
    try {
      return valueOf(name);
    }
    catch (Exception e) {
      LOG.error("Cant find RemoteConnectionType with the name " + name, e);
      return NONE;
    }
  }
}
