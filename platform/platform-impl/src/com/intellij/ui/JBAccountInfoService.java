// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Consumer;

public interface JBAccountInfoService {

  final class JBAData {
    @NotNull
    public final String userId;
    @Nullable
    public final String loginName;
    @Nullable
    public final String userEmail;

    public JBAData(@NotNull String userId, @Nullable String loginName, @Nullable String email) {
      this.userId = userId;
      this.loginName = loginName;
      userEmail = email;
    }
  }

  @Nullable
  JBAccountInfoService.JBAData getUserId();

  void invokeJBALogin(@Nullable Consumer<String> userIdConsumer, @Nullable Runnable onFailure);

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
