package com.intellij.remote;

import com.intellij.execution.ExecutionException;

import java.net.NoRouteToHostException;

/**
 * @author traff
 */
public class RemoteSdkException extends ExecutionException {
  private final boolean myNoRouteToHost;
  private final boolean myAuthFailed;

  public RemoteSdkException(String s, Throwable throwable) {
    super(s, throwable);
    myNoRouteToHost = throwable instanceof NoRouteToHostException;
    myAuthFailed = false;
  }

  public RemoteSdkException(String s) {
    super(s);
    myAuthFailed = false;
    myNoRouteToHost = false;
  }

  public boolean isNoRouteToHost() {
    return myNoRouteToHost;
  }

  public boolean isAuthFailed() {
    return myAuthFailed;
  }

  public String getMessage() {
    if (myNoRouteToHost) {
      return getCause().getMessage();
    }
    else if (myAuthFailed) {
      return "Authentication failed";
    }
    else {
      return super.getMessage();
    }
  }

  public static RemoteSdkException cantObtainRemoteCredentials(Throwable e) {
    return new RemoteSdkException("Cant obtain remote credentials", e);
  }
}
