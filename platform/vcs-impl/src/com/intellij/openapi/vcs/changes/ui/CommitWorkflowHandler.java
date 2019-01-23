// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CommitWorkflowHandler {
  //TODO move to VcsDataKeys
  DataKey<CommitWorkflowHandler> DATA_KEY = DataKey.create("Vcs.CommitWorkflowHandler");

  @Nullable
  CommitExecutor getExecutor(@NotNull String executorId);

  boolean isExecutorEnabled(@NotNull CommitExecutor executor);

  void execute(@NotNull CommitExecutor executor);
}