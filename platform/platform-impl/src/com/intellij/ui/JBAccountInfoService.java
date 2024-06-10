// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface JBAccountInfoService {

  final class JBAData {
    public final @NotNull String id;
    @NlsSafe public final @Nullable String loginName;
    @NlsSafe public final @Nullable String email;
    @NlsSafe public final @Nullable String presentableName;

    public JBAData(@NotNull String userId, @Nullable String loginName, @Nullable String email, @Nullable String presentableName) {
      this.id = userId;
      this.loginName = loginName;
      this.email = email;
      this.presentableName = presentableName;
    }
  }

  @Nullable
  JBAccountInfoService.JBAData getUserData();

  default @Nullable String getIdToken() {
    return null;
  }

  default @NotNull Future<String> getAccessToken() {
    return CompletableFuture.completedFuture(null);
  }

  void invokeJBALogin(@Nullable Consumer<? super String> userIdConsumer, @Nullable Runnable onFailure);

  static @Nullable JBAccountInfoService getInstance() {
    return JBAccountInfoServiceHolder.INSTANCE;
  }
}
