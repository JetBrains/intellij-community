package com.intellij.remote;

import com.intellij.execution.ExecutionException;

import java.net.NoRouteToHostException;

/**
 * @author traff
 */
public class RemoteSdkException extends ExecutionException {
  private final boolean myNoRouteToHost;
  private final boolean myAuthFailed;

  private Throwable myCause;

  public RemoteSdkException(String s, Throwable throwable) {
    super(s, throwable);

    myAuthFailed = false;
    Throwable t = throwable;
    while (t != null) {
      if (t instanceof NoRouteToHostException) {
        myCause = t;
        myNoRouteToHost = true;
        return;
      }

      t = t.getCause();
    }
    myNoRouteToHost = false;
    myCause = throwable;
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
      return myCause.getMessage();
    }
    else if (myAuthFailed) {
      return "Authentication failed";
    }
    else {
      return super.getMessage();
    }
  }

  public static RemoteSdkException cantObtainRemoteCredentials(Throwable e) {
    // TODO needs review
    if (e.getCause() instanceof RemoteCredentialException) {
      return new RemoteSdkException("Cant obtain remote credentials", e);
    }
    else {
      return new RemoteSdkException(e.getMessage(), e);
    }
  }
}
