// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionGroupExpander;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.List;

final class ActionGroupExpanderImpl implements ActionGroupExpander {
  @Override
  public boolean allowsFastUpdate(@Nullable Project project, @NotNull String place) {
    return true;
  }

  @Override
  public @NotNull CancellablePromise<List<AnAction>> expandActionGroupAsync(@Nullable Project project,
                                                                            @NotNull DataContext context,
                                                                            @NotNull String place,
                                                                            @NotNull ActionGroup group,
                                                                            boolean hideDisabled,
                                                                            @NotNull Delegate delegate) {
    return delegate.expandActionGroupAsync(group, hideDisabled);
  }
}
