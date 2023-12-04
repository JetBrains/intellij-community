// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface JBAccountInfoService {

  final class JBAData {
    public final @NotNull String id;
    @NlsSafe public final @Nullable String loginName;
    @NlsSafe public final @Nullable String email;

    public JBAData(@NotNull String userId, @Nullable String loginName, @Nullable String email) {
      this.id = userId;
      this.loginName = loginName;
      this.email = email;
    }
  }

  @Nullable
  JBAccountInfoService.JBAData getUserData();

  default @Nullable String getIdToken() {
    return null;
  }

  default @NotNull Future<String> getAccessToken() {
    return new Future<>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return true;
      }

      @Override
      public String get() {
        return null;
      }

      @Override
      public String get(long timeout, @NotNull TimeUnit unit) {
        return null;
      }
    };
  }

  void invokeJBALogin(@Nullable Consumer<? super String> userIdConsumer, @Nullable Runnable onFailure);

  static @Nullable JBAccountInfoService getInstance() {
    return JBAccountInfoServiceHolder.INSTANCE;
  }
}
