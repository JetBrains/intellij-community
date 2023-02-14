// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsActions;
import com.intellij.vcs.commit.CommitWorkflowHandlerState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface CommitExecutorWithRichDescription extends CommitExecutor {
  @Nullable @NlsActions.ActionText String getText(@NotNull CommitWorkflowHandlerState state);
}
