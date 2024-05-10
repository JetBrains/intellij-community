// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.platform.util.coroutines.CoroutineScopeKt;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
public final class UsageViewCoroutineScopeProvider {
  private final CoroutineScope myCoroutineScope;

  public UsageViewCoroutineScopeProvider(@NotNull CoroutineScope coroutineScope) {
    // Create a child supervisor scope because we don't want this scope to cancel when some child coroutine fails.
    // We want it to LOG.error.
    myCoroutineScope = CoroutineScopeKt.childScope(coroutineScope, getClass().getName(), EmptyCoroutineContext.INSTANCE, true);
  }

  public static UsageViewCoroutineScopeProvider getInstance(@NotNull Project project) {
    return project.getService(UsageViewCoroutineScopeProvider.class);
  }

  /**
   *  This service lifetime could be used in UI components
   *  as coroutine scope for various BGT activities.
   *  This scope is supervised, so failures under it won't cancel the entire provider scope.
   */
  public CoroutineScope getCoroutineScope() {
    return myCoroutineScope;
  }
}
