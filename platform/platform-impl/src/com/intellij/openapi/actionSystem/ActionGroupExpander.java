// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.List;

public interface ActionGroupExpander {
  @FunctionalInterface
  interface Delegate {
    @NotNull CancellablePromise<List<AnAction>> expandActionGroupAsync(@NotNull ActionGroup group, boolean hideDisabled);
  }

  static @NotNull ActionGroupExpander getInstance() {
    return ApplicationManager.getApplication().getService(ActionGroupExpander.class);
  }

  boolean allowsFastUpdate(@Nullable Project project, @NotNull String place);

  @NotNull CancellablePromise<List<AnAction>> expandActionGroupAsync(@Nullable Project project,
                                                                     @NotNull DataContext context,
                                                                     @NotNull String place,
                                                                     @NotNull ActionGroup group,
                                                                     boolean hideDisabled,
                                                                     @NotNull Delegate delegate);
}
