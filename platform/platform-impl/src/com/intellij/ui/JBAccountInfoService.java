// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface JBAccountInfoService {

  final class JBAData {
    @NotNull
    public final String id;
    @Nullable
    public final String loginName;
    @Nullable
    public final String email;

    public JBAData(@NotNull String userId, @Nullable String loginName, @Nullable String email) {
      this.id = userId;
      this.loginName = loginName;
      this.email = email;
    }
  }

  @Nullable
  JBAccountInfoService.JBAData getUserData();

  @Nullable
  default String getIdToken() {
    return null;
  }

  @NotNull
  default Future<String> getAccessToken() {
    return new Future<String>() {
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

  @Nullable
  static JBAccountInfoService getInstance() {
    try {
      return ServiceLoader.load(JBAccountInfoService.class).findFirst().orElse(null);
    }
    catch (ServiceConfigurationError ignored) {
      return null;
    }
  }
}
