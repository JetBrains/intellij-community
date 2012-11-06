package com.intellij.remotesdk;

import com.intellij.execution.ExecutionException;

import java.net.NoRouteToHostException;

/**
 * @author traff
 */
public class RemoteInterpreterException extends ExecutionException {
  private final boolean myNoRouteToHost;
  private final boolean myAuthFailed;

  public RemoteInterpreterException(String s, Throwable throwable) {
    super(s, throwable);
    myNoRouteToHost = throwable instanceof NoRouteToHostException;
    myAuthFailed = false;
  }

  public RemoteInterpreterException(String s) {
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
}
