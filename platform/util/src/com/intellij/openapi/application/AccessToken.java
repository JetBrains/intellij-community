// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AccessToken implements AutoCloseable {
  @Override
  public final void close() {
    finish();
  }

  public abstract void finish();

  public static final @NotNull AccessToken EMPTY_ACCESS_TOKEN = new AccessToken() {
    @Override
    public void finish() { }
  };
  
  public static @NotNull AccessToken create(@NotNull Runnable onFinish) {
    return new AccessToken() {
      @Override
      public void finish() {
        onFinish.run();
      }
    };
  }

  public static @NotNull AccessToken compound(@NotNull List<@NotNull AccessToken> tokens) {
    if (tokens.isEmpty()) {
      return EMPTY_ACCESS_TOKEN;
    }
    if (tokens.size() == 1) {
      return tokens.get(0);
    }
    return new CompoundAccessToken(tokens);
  }
}
