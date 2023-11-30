// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
public final class UsageViewCoroutineScopeProvider {
  private final CoroutineScope myCoroutineScope;

  public UsageViewCoroutineScopeProvider(@NotNull CoroutineScope coroutineScope) {
    myCoroutineScope = coroutineScope;
  }

  public static UsageViewCoroutineScopeProvider getInstance(@NotNull Project project) {
    return project.getService(UsageViewCoroutineScopeProvider.class);
  }

  // This service lifetime, could be used in UI components
  // as coroutine scope for various BGT activities
  public CoroutineScope getCoroutineScope() {
    return myCoroutineScope;
  }
}
