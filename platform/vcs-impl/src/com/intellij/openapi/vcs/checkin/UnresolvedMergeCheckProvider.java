// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class UnresolvedMergeCheckProvider {
  public static final ExtensionPointName<UnresolvedMergeCheckProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.unresolvedMergeCheckProvider");

  public abstract @Nullable CheckinHandler.ReturnResult checkUnresolvedConflicts(@NotNull CheckinProjectPanel panel,
                                                                                 @NotNull CommitContext commitContext,
                                                                                 @NotNull CommitInfo commitInfo);
}
