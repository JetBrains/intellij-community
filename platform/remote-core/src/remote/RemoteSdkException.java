// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.execution.ExecutionExceptionWithAttachments;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.NoRouteToHostException;

public class RemoteSdkException extends ExecutionExceptionWithAttachments {
  private final boolean myNoRouteToHost;
  private final boolean myAuthFailed;

  private Throwable myCause;

  public RemoteSdkException(@DialogMessage String s, Throwable throwable) {
    this(s, throwable, null, null);
  }

  public RemoteSdkException(@DialogMessage String s, Throwable throwable, @Nullable String stdout, @Nullable String stderr) {
    super(s, throwable, stdout, stderr);

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
    this(s, null, null);
  }

  public RemoteSdkException(@DialogMessage String s, @Nullable String stdout, @Nullable String stderr) {
    super(s, stdout, stderr);
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
      return RemoteBundle.message("authentication.failed");
    }
    else {
      return super.getMessage();
    }
  }

  public static @NotNull RemoteSdkException cantObtainRemoteCredentials(@NotNull Throwable e) {
    if (e.getCause() instanceof RemoteCredentialException) {
      return new RemoteSdkException(RemoteBundle.message("remote.sdk.exception.cant.obtain.remote.credentials"), e);
    }
    else if (e instanceof RemoteSdkException) {
      return (RemoteSdkException)e;
    }
    else {
      return new RemoteSdkException(e.getMessage(), e);
    }
  }
}
