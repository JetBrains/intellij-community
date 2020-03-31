// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

public abstract class AccessToken implements AutoCloseable {
  @Override
  public final void close() {
    finish();
  }

  public abstract void finish();

  @NotNull
  public static final AccessToken EMPTY_ACCESS_TOKEN = new AccessToken() {
    @Override
    public void finish() {}
  };
}
