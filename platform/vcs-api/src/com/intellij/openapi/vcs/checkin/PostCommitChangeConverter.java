// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PostCommitChangeConverter {
  @NotNull
  @RequiresBackgroundThread
  List<Change> convertChangesAfterCommit(@NotNull List<Change> changes, @NotNull CommitContext commitContext);
}
