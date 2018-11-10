// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
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
