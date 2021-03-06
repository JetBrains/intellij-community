// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.NlsContexts.DialogMessage;

import java.net.NoRouteToHostException;

public class RemoteSdkException extends ExecutionException {
  private final boolean myNoRouteToHost;
  private final boolean myAuthFailed;

  private Throwable myCause;

  public RemoteSdkException(@DialogMessage String s, Throwable throwable) {
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

  public RemoteSdkException(@DialogMessage String s) {
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

  @Override
  public String getMessage() {
    if (myNoRouteToHost) {
      return myCause.getMessage();
    }
    else if (myAuthFailed) {
      return IdeBundle.message("authentication.failed");
    }
    else {
      return super.getMessage();
    }
  }

  public static RemoteSdkException cantObtainRemoteCredentials(Throwable e) {
    // TODO needs review
    if (e.getCause() instanceof RemoteCredentialException) {
      return new RemoteSdkException(IdeBundle.message("remote.sdk.exception.cant.obtain.remote.credentials"), e);
    }
    else {
      return new RemoteSdkException(e.getMessage(), e);
    }
  }
}
