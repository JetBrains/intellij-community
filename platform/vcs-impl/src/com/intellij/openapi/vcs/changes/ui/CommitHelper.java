// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.util.NullableFunction;
import com.intellij.vcs.commit.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * @deprecated use Committer directly
 */
@Deprecated(forRemoval = true)
public class CommitHelper {
  private final @Nls @NotNull String myActionName;
  private final boolean myForceSyncCommit;
  @NotNull private final AbstractCommitter myCommitter;

  public CommitHelper(@NotNull Project project,
                      @NotNull ChangeList changeList,
                      @NotNull List<? extends Change> changes,
                      @Nls @NotNull String actionName,
                      @NotNull String commitMessage,
                      @NotNull List<? extends CheckinHandler> handlers,
                      boolean isDefaultChangeListFullyIncluded,
                      boolean synchronously,
                      @NotNull NullableFunction<Object, Object> additionalData,
                      @Nullable CommitResultHandler resultHandler) {
    myActionName = actionName;
    myForceSyncCommit = synchronously;

    ChangeListCommitState commitState = new ChangeListCommitState((LocalChangeList)changeList, changes, commitMessage);
    // for compatibility with external plugins
    CommitContext commitContext =
      additionalData instanceof PseudoMap ? ((PseudoMap<Object, Object>)additionalData).getCommitContext() : new CommitContext();
    myCommitter = new SingleChangeListCommitter(project, commitState, commitContext, actionName, isDefaultChangeListFullyIncluded);

    myCommitter.addResultHandler(new CommitHandlersNotifier(handlers));
    myCommitter.addResultHandler(notNull(resultHandler, new ShowNotificationCommitResultHandler(myCommitter)));
  }

  @SuppressWarnings("unused") // Required for compatibility with external plugins.
  public boolean doCommit() {
    myCommitter.runCommit(myActionName, myForceSyncCommit);
    return true;
  }
}
