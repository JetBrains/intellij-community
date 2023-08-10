// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

public abstract class AccessToken implements AutoCloseable {
  @Override
  public final void close() {
    finish();
  }

  public abstract void finish();

  public static final @NotNull AccessToken EMPTY_ACCESS_TOKEN = new AccessToken() {
    @Override
    public void finish() {}
  };
}
