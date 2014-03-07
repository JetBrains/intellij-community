package com.intellij.remote;

import com.intellij.remote.MutableRemoteCredentials;
import com.intellij.remote.RemoteSdkProperties;

/**
 * @author traff
 */
public interface RemoteSdkCredentials extends MutableRemoteCredentials, RemoteSdkProperties {
  String getFullInterpreterPath();
}
