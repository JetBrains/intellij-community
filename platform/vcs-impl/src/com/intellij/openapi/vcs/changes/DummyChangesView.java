/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
* @author irengrig
*/
class DummyChangesView implements ChangesViewEx {
  DummyChangesView(Project project) {
  }

  @Override
  public void scheduleRefresh() {
  }

  @Override
  public void selectFile(VirtualFile vFile) {
  }

  @Override
  public void selectChanges(@NotNull List<? extends Change> changes) {
  }

  @Override
  public void updateProgressText(String text, boolean isError) {
  }

  @Override
  public void setBusy(boolean b) {
  }

  @Override
  public void setGrouping(@NotNull String groupingKey) {
  }

  @Override
  public void refreshImmediately() {
  }

  @Override
  public void updateCommitWorkflow() {
  }

  @Nullable
  @Override
  public ChangesViewCommitWorkflowHandler getCommitWorkflowHandler() {
    return null;
  }

  @Override
  public boolean isAllowExcludeFromCommit() {
    return false;
  }
}
