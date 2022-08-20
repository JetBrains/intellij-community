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

/**
 * @deprecated use Committer directly
 */
@Deprecated(forRemoval = true)
public class CommitHelper {
  private final @Nls @NotNull String myActionName;
  private final boolean myForceSyncCommit;
  @NotNull private final VcsCommitter myCommitter;

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
    myCommitter = SingleChangeListCommitter.create(project, commitState, commitContext, actionName);

    myCommitter.addResultHandler(new CheckinHandlersNotifier(myCommitter, handlers));
    if (resultHandler != null) {
      myCommitter.addResultHandler(new CommitResultHandlerNotifier(myCommitter, resultHandler));
    }
    else {
      myCommitter.addResultHandler(new ShowNotificationCommitResultHandler(myCommitter));
    }
  }

  @SuppressWarnings("unused") // Required for compatibility with external plugins.
  public boolean doCommit() {
    myCommitter.runCommit(myActionName, myForceSyncCommit);
    return true;
  }
}
