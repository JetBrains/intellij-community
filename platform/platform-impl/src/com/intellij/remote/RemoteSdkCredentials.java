package com.intellij.remote;

/**
 * @author traff
 */
public interface RemoteSdkCredentials extends MutableRemoteCredentials, RemoteSdkProperties {
  String getFullInterpreterPath();
}
