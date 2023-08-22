// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

final class CancellableLaterEdtInvoker {
  private final @NotNull Project myProject;
  private volatile @Nullable ModalityState myDumbStartModality;
  private final AtomicInteger myCancellationCounter = new AtomicInteger();

  CancellableLaterEdtInvoker(@NotNull Project project) {
    myProject = project;
  }

  void cancelAllPendingTasks() {
    myCancellationCounter.incrementAndGet();
  }

  void invokeLaterWithDefaultModality(@NotNull Runnable runnable, @Nullable Runnable onCancelRunnable) {
    new TrackedEdtActivity(runnable, onCancelRunnable).invokeLaterWithModality(ModalityState.defaultModalityState());
  }

  void invokeLaterWithDumbStartModality(@NotNull Runnable runnable) {
    ModalityState modality = myDumbStartModality;
    if (modality != null) {
      new TrackedEdtActivity(runnable).invokeLaterWithModality(modality);
    }
    else {
      Logger.getInstance(CancellableLaterEdtInvoker.class).info("invokeLaterWithDumbStartModality does nothing, because modality is null");
    }
  }

  void setDumbStartModality(@Nullable ModalityState modality) {
    myDumbStartModality = modality;
  }

  private class TrackedEdtActivity implements Runnable {
    private final @NotNull Runnable myRunnable;
    private final @Nullable Runnable myCancelationRunnable;
    private final int cancellationCounterValueWhenCreated;

    TrackedEdtActivity(@NotNull Runnable runnable) {
      this(runnable, null);
    }

    TrackedEdtActivity(@NotNull Runnable runnable, @Nullable Runnable cancelationRunnable) {
      myRunnable = runnable;
      myCancelationRunnable = cancelationRunnable;
      cancellationCounterValueWhenCreated = myCancellationCounter.get();
    }

    void invokeLaterWithModality(@NotNull ModalityState modalityState) {
      ApplicationManager.getApplication().invokeLater(this, modalityState);
    }

    @Override
    public void run() {
      if (cancellationCounterValueWhenCreated == myCancellationCounter.get() && !myProject.isDisposed()) {
        myRunnable.run();
      }
      else if (myCancelationRunnable != null) {
        myCancelationRunnable.run();
      }
    }
  }
}
