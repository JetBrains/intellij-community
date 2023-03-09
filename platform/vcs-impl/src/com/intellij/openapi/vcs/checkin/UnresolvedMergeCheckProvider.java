// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class UnresolvedMergeCheckProvider {
  public static final ExtensionPointName<UnresolvedMergeCheckProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.unresolvedMergeCheckProvider");

  @Nullable
  public abstract CheckinHandler.ReturnResult checkUnresolvedConflicts(@NotNull CheckinProjectPanel panel,
                                                                       @NotNull CommitContext commitContext,
                                                                       @NotNull CommitInfo commitInfo);
}
