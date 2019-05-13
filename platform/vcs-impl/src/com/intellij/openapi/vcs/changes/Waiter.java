// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class Waiter extends Task.Modal {
  private static final Logger LOG = Logger.getInstance(Waiter.class);

  @NotNull private final Runnable myRunnable;
  @NotNull private final AtomicBoolean myStarted = new AtomicBoolean();
  @NotNull private final Semaphore mySemaphore = new Semaphore();

  public Waiter(@NotNull Project project, @NotNull Runnable runnable, String title, boolean cancellable) {
    super(project, VcsBundle.message("change.list.manager.wait.lists.synchronization", title), cancellable);
    myRunnable = runnable;
    mySemaphore.down();
    setCancelText("Skip");
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    indicator.setText2(VcsBundle.message("commit.wait.util.synched.text"));

    if (!myStarted.compareAndSet(false, true)) {
      LOG.error("Waiter running under progress being started again.");
    }
    else {
      while (!mySemaphore.waitFor(500)) {
        indicator.checkCanceled();
      }
    }
  }

  @Override
  public void onCancel() {
    onSuccess();
  }

  @Override
  public void onSuccess() {
    // Be careful with changes here as "Waiter.onSuccess()" is explicitly invoked from "FictiveBackgroundable"
    if (!myProject.isDisposed()) {
      myRunnable.run();
      ChangesViewManager.getInstance(myProject).scheduleRefresh();
    }
  }

  public void done() {
    mySemaphore.up();
  }
}
