/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorListenerAdapter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class ProgressSuspender {
  private static final Key<ProgressSuspender> PROGRESS_SUSPENDER = Key.create("PROGRESS_SUSPENDER");
  public static final Topic<SuspenderListener> TOPIC = Topic.create("ProgressSuspender", SuspenderListener.class);

  private final Object myLock = new Object();
  private final Thread myThread;
  private static final Application ourApp = ApplicationManager.getApplication();
  @NotNull private final String mySuspendedText;
  @Nullable private String myTempReason;
  private final SuspenderListener myPublisher;
  private volatile boolean mySuspended;
  private final CoreProgressManager.CheckCanceledHook myHook = this::freezeIfNeeded;

  private ProgressSuspender(@NotNull ProgressIndicatorEx progress, @NotNull String suspendedText) {
    mySuspendedText = suspendedText;
    assert progress.isRunning();
    assert ProgressIndicatorProvider.getGlobalProgressIndicator() == progress;
    myThread = Thread.currentThread();
    myPublisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC);

    ((UserDataHolder) progress).putUserData(PROGRESS_SUSPENDER, this);
    
    new ProgressIndicatorListenerAdapter() {
      @Override
      public void cancelled() {
        resumeProcess();
      }
    }.installToProgress(progress);

    myPublisher.suspendableProgressAppeared(this);
  }

  public static ProgressSuspender markSuspendable(@NotNull ProgressIndicator indicator, @NotNull String suspendedText) {
    return new ProgressSuspender((ProgressIndicatorEx)indicator, suspendedText);
  }

  @Nullable
  public static ProgressSuspender getSuspender(@NotNull ProgressIndicator indicator) {
    return indicator instanceof UserDataHolder ? ((UserDataHolder)indicator).getUserData(PROGRESS_SUSPENDER) : null;
  }

  @NotNull
  public String getSuspendedText() {
    synchronized (myLock) {
      return myTempReason != null ? myTempReason : mySuspendedText;
    }
  }

  public boolean isSuspended() {
    return mySuspended;
  }

  /**
   * @param reason if provided, is displayed in the UI instead of suspended text passed into constructor until the progress is resumed
   */
  public void suspendProcess(@Nullable String reason) {
    synchronized (myLock) {
      if (mySuspended) return;

      mySuspended = true;
      myTempReason = reason;

      ((ProgressManagerImpl)ProgressManager.getInstance()).addCheckCanceledHook(myHook);
    }

    myPublisher.suspendedStatusChanged(this);
  }

  public void resumeProcess() {
    synchronized (myLock) {
      if (!mySuspended) return;

      mySuspended = false;
      myTempReason = null;

      ((ProgressManagerImpl)ProgressManager.getInstance()).removeCheckCanceledHook(myHook);

      myLock.notifyAll();
    }
    
    myPublisher.suspendedStatusChanged(this);
  }

  private boolean freezeIfNeeded(@Nullable ProgressIndicator current) {
    if (current == null || ourApp.isReadAccessAllowed() || !CoreProgressManager.isThreadUnderIndicator(current, myThread)) {
      return false;
    }

    synchronized (myLock) {
      while (mySuspended) {
        try {
          myLock.wait(10000);
        }
        catch (InterruptedException ignore) {
        }
      }

      return true;
    }
  }
  
  public interface SuspenderListener {
    /** Called (on any thread) when a new progress is created with suspension capability */
    default void suspendableProgressAppeared(@NotNull ProgressSuspender suspender) {}
    
    /** Called (on any thread) when a progress is suspended or resumed */
    default void suspendedStatusChanged(@NotNull ProgressSuspender suspender) {}
  }

}
