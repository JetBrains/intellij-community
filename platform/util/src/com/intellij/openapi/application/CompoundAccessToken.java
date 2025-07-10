// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class CompoundAccessToken extends AccessToken {

  private final List<AccessToken> myTokens;

  CompoundAccessToken(@NotNull List<AccessToken> tokens) {
    if (tokens.isEmpty()) {
      throw new IllegalArgumentException("tokens must not be empty");
    }
    myTokens = tokens;
  }

  @Override
  public void finish() {
    List<Throwable> throwables = null;
    for (AccessToken token : myTokens) {
      try {
        token.finish();
      }
      catch (Throwable t) {
        if (throwables == null) {
          throwables = new SmartList<>();
        }
        throwables.add(t);
      }
    }
    if (throwables != null) {
      Throwable throwable = throwables.get(0);
      for (int i = 1; i < throwables.size(); i++) {
        throwable.addSuppressed(throwables.get(i));
      }
      ExceptionUtil.rethrow(throwable);
    }
  }
}
