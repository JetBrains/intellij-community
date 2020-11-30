// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler;
import org.jetbrains.annotations.Nullable;

public interface ChangesViewEx extends ChangesViewI {
  void refreshImmediately();

  @RequiresEdt
  void updateCommitWorkflow();

  @Nullable
  ChangesViewCommitWorkflowHandler getCommitWorkflowHandler();

  boolean isAllowExcludeFromCommit();
}
