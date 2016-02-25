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

/*
 * @author max
 */
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.util.EventDispatcher;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EventListener;
import java.util.Set;

public class HeavyProcessLatch {
  public static final HeavyProcessLatch INSTANCE = new HeavyProcessLatch();
  private static final String UI_ACTIVITY = "UI Activity";

  private final Set<String> myHeavyProcesses = new THashSet<String>();
  private final EventDispatcher<HeavyProcessListener> myEventDispatcher = EventDispatcher.create(HeavyProcessListener.class);
  private volatile Thread myUiActivityThread;

  private HeavyProcessLatch() {
  }

  /**
   * @deprecated use {@link #processStarted(String)} instead
   */
  @Deprecated
  public void processStarted() {
    processStarted("");
  }

  @NotNull
  public AccessToken processStarted(@NotNull final String operationName) {
    synchronized (myHeavyProcesses) {
      myHeavyProcesses.add(operationName);
    }
    myEventDispatcher.getMulticaster().processStarted();
    return new AccessToken() {
      @Override
      public void finish() {
        processFinished(operationName);
      }
    };
  }

  /**
   * @deprecated use {@link #processStarted(String)} instead
   */
  @Deprecated
  public void processFinished() {
    processFinished("");
  }

  private void processFinished(@NotNull String operationName) {
    synchronized (myHeavyProcesses) {
      myHeavyProcesses.remove(operationName);
    }
    myEventDispatcher.getMulticaster().processFinished();
  }

  public boolean isRunning() {
    synchronized (myHeavyProcesses) {
      return !myHeavyProcesses.isEmpty();
    }
  }

  public String getRunningOperationName() {
    synchronized (myHeavyProcesses) {
      return myHeavyProcesses.isEmpty() ? null : myHeavyProcesses.iterator().next();
    }
  }


  public interface HeavyProcessListener extends EventListener {
    void processStarted();
    void processFinished();
  }

  public void addListener(@NotNull Disposable parentDisposable, @NotNull HeavyProcessListener listener) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  /**
   * Gives current event processed on Swing thread higher priority
   * @see #stopThreadPrioritizing()
   */
  public void prioritizeUiActivity() {
    myUiActivityThread = Thread.currentThread();
    processStarted(UI_ACTIVITY);
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        stopThreadPrioritizing();
      }
    });
  }

  /**
   * Removes priority from Swing thread, if present. Should be invoked before a thread starts waiting for other threads in idle mode,
   * to ensure those other threads complete ASAP.
   * @see #prioritizeUiActivity()
   */
  public void stopThreadPrioritizing() {
    myUiActivityThread = null;
    processFinished(UI_ACTIVITY);
  }

  /**
   * @return whether there is a prioritized thread, but not the current one
   */
  public boolean isInsideLowPriorityThread() {
    Thread thread = myUiActivityThread;
    return thread != null && thread != Thread.currentThread();
  }

  /**
   * @return whether there is a prioritized thread currently
   */
  public boolean hasPrioritizedThread() {
    return myUiActivityThread != null;
  }
}