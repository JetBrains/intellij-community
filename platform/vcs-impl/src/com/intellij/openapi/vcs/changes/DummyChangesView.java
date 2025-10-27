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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Supplier;

class DummyChangesView implements ChangesViewEx {
  DummyChangesView() {
  }

  @Override
  public void scheduleRefresh() {
  }

  @Override
  public void resetViewImmediatelyAndRefreshLater() {
  }

  @Override
  public void selectFile(VirtualFile vFile) {
  }

  @Override
  public void selectChanges(@NotNull List<? extends Change> changes) {
  }

  @Override
  public void updateProgressComponent(@NotNull List<Supplier<JComponent>> progress) {
  }

  @Override
  public void setGrouping(@NotNull String groupingKey) {
  }

  @Override
  public void scheduleRefresh(@NotNull Runnable callback) {
  }

  @Override
  public boolean isAllowExcludeFromCommit() {
    return false;
  }

  @Override
  public @Nullable ChangesViewCommitWorkflowHandler getCommitWorkflowHandler() {
    return null;
  }
}
