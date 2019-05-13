/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class BeforeCheckinDialogHandler {
  /**
   * @deprecated see {@link #beforeCommitDialogShown(com.intellij.openapi.project.Project, java.util.List, Iterable, boolean)}
   * @return false to cancel commit
   */
  @Deprecated
  public boolean beforeCommitDialogShownCallback(Iterable<CommitExecutor> executors, boolean showVcsCommit) {
    throw new AbstractMethodError();
  }

  /**
   * @return false to cancel commit
   */
  public boolean beforeCommitDialogShown(@NotNull Project project, @NotNull List<Change> changes, @NotNull Iterable<CommitExecutor> executors, boolean showVcsCommit) {
    //noinspection deprecation
    return beforeCommitDialogShownCallback(executors, showVcsCommit);
  }
}
