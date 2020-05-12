// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;

@Service
public final class DumbServiceSyncTaskQueue {
  public void runTaskSynchronously(@NotNull DumbModeTask task) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null) {
      indicator = new EmptyProgressIndicator();
    }

    indicator.pushState();
    ((CoreProgressManager)ProgressManager.getInstance()).suppressPrioritizing();
    try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing task", HeavyProcessLatch.Type.Indexing)) {
      task.performInDumbMode(indicator);
    }
    finally {
      ((CoreProgressManager)ProgressManager.getInstance()).restorePrioritizing();
      indicator.popState();
      Disposer.dispose(task);
    }
  }
}
