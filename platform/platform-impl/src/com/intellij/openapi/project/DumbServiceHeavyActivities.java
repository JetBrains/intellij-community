// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class DumbServiceHeavyActivities {
  private volatile ProgressSuspender myCurrentSuspender;
  private final List<String> myRequestedSuspensions = ContainerUtil.createEmptyCOWList();


  void suspendIndexingAndRun(@NlsContexts.ProgressText @NotNull String activityName, @NotNull Runnable activity) {
    try (AccessToken ignore = heavyActivityStarted(activityName)) {
      activity.run();
    }
  }

  @NotNull AccessToken heavyActivityStarted(@NlsContexts.ProgressText @NotNull String activityName) {
    String reason = IdeBundle.message("dumb.service.indexing.paused.due.to", activityName);
    synchronized (myRequestedSuspensions) {
      myRequestedSuspensions.add(reason);
    }

    suspendCurrentTask(reason);
    return new AccessToken() {
      @Override
      public void finish() {
        synchronized (myRequestedSuspensions) {
          myRequestedSuspensions.remove(reason);
        }
        resumeAutoSuspendedTask(reason);
      }
    };
  }

  boolean isSuspended() {
    ProgressSuspender suspender = myCurrentSuspender;
    return suspender != null && suspender.isSuspended();
  }

  void resumeProgressIfPossible() {
    ProgressSuspender suspender = myCurrentSuspender;
    if (suspender != null && suspender.isSuspended()) {
      suspender.resumeProcess();
    }
  }

  void resetCurrentSuspender() {
    myCurrentSuspender = null;
  }

  void setCurrentSuspenderAndSuspendIfRequested(@Nullable ProgressSuspender suspender) {
    myCurrentSuspender = suspender;
    suspendIfRequested(suspender);
  }

  private void resumeAutoSuspendedTask(@NlsContexts.ProgressText @NotNull String reason) {
    ProgressSuspender currentSuspender = myCurrentSuspender;
    if (currentSuspender != null && currentSuspender.isSuspended() && reason.equals(currentSuspender.getSuspendedText())) {
      currentSuspender.resumeProcess();
    }
  }

  private void suspendIfRequested(ProgressSuspender suspender) {
    String suspendedReason;
    synchronized (myRequestedSuspensions) {
      suspendedReason = ContainerUtil.getLastItem(myRequestedSuspensions);
    }
    if (suspendedReason != null) {
      suspender.suspendProcess(suspendedReason);
    }
  }

  private void suspendCurrentTask(@NlsContexts.ProgressText String reason) {
    ProgressSuspender currentSuspender = myCurrentSuspender;
    if (currentSuspender != null && !currentSuspender.isSuspended()) {
      currentSuspender.suspendProcess(reason);
    }
  }
}
