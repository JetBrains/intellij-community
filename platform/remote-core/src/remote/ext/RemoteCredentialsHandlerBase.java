// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext;

public abstract class RemoteCredentialsHandlerBase<T> implements RemoteCredentialsHandler {
  private final T myHolder;

  public RemoteCredentialsHandlerBase(T credentials) {
    myHolder = credentials;
  }

  protected final T getCredentials() {
    return myHolder;
  }
}
