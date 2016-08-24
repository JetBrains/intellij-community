/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.WaitForProgressToShow.runOrInvokeLaterAboveProgress;

class FictiveBackgroundable extends Task.Backgroundable {
  @NotNull private final Waiter myWaiter;
  @Nullable private final ModalityState myState;

  FictiveBackgroundable(@NotNull Project project,
                        @NotNull Runnable runnable,
                        String title,
                        boolean cancellable,
                        @Nullable ModalityState state) {
    super(project, VcsBundle.message("change.list.manager.wait.lists.synchronization", title), cancellable);
    myState = state;
    myWaiter = new Waiter(project, runnable, title, cancellable);
  }

  public void run(@NotNull ProgressIndicator indicator) {
    myWaiter.run(indicator);
    runOrInvokeLaterAboveProgress(() -> myWaiter.onSuccess(), notNull(myState, ModalityState.NON_MODAL), myProject);
  }

  @Override
  public boolean isHeadless() {
    return false;
  }

  public void done() {
    myWaiter.done();
  }
}
