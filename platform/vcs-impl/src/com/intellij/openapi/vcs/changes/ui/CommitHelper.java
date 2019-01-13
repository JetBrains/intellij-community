// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.CommitResultHandler;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;

@Deprecated
public class CommitHelper {
  @NotNull private final String myActionName;
  private final boolean myForceSyncCommit;
  @NotNull private final AbstractCommitter myCommitter;

  public CommitHelper(@NotNull Project project,
                      @NotNull ChangeList changeList,
                      @NotNull List<? extends Change> changes,
                      @NotNull String actionName,
                      @NotNull String commitMessage,
                      @NotNull List<? extends CheckinHandler> handlers,
                      boolean isDefaultChangeListFullyIncluded,
                      boolean synchronously,
                      @NotNull NullableFunction<Object, Object> additionalData,
                      @Nullable CommitResultHandler resultHandler) {
    myActionName = actionName;
    myForceSyncCommit = synchronously;
    myCommitter =
      new SingleChangeListCommitter(project, (LocalChangeList)changeList, changes, commitMessage, handlers, additionalData, null,
                                    actionName, isDefaultChangeListFullyIncluded);

    myCommitter.addResultHandler(notNull(resultHandler, new DefaultCommitResultHandler(myCommitter)));
  }

  @SuppressWarnings("unused") // Required for compatibility with external plugins.
  public boolean doCommit() {
    myCommitter.runCommit(myActionName, myForceSyncCommit);
    return true;
  }
}
