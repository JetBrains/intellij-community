// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.execution.ExecutionExceptionWithAttachments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.NoRouteToHostException;

public class RemoteSdkException extends ExecutionExceptionWithAttachments {
  private final Throwable myCause;
  private static final Logger LOG = Logger.getInstance(RemoteSdkException.class);

  public RemoteSdkException(@DialogMessage String s, Throwable throwable) {
    this(s, throwable, null, null);
  }

  public RemoteSdkException(@DialogMessage String s, Throwable throwable, @Nullable String stdout, @Nullable String stderr) {
    super(s, throwable, stdout, stderr);

    Throwable t = throwable;
    while (t != null) {
      if (t instanceof NoRouteToHostException) {
        myCause = t;
        return;
      }
      t = t.getCause();
    }
    myCause = throwable;
  }

  public RemoteSdkException(@DialogMessage String s) {
    this(s, null, null);
  }

  public RemoteSdkException(@DialogMessage String s, @Nullable String stdout, @Nullable String stderr) {
    super(s, stdout, stderr);
    LOG.warn(String.format("message %s, out: %s, err: %s", s, stdout, stderr));
    myCause = null;
  }

  @Override
  public String getMessage() {
    return myCause instanceof NoRouteToHostException ? myCause.getMessage() : super.getMessage();
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
