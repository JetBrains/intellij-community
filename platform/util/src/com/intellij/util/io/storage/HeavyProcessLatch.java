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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.EventDispatcher;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class HeavyProcessLatch {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.storage.HeavyProcessLatch");
  public static final HeavyProcessLatch INSTANCE = new HeavyProcessLatch();

  private final Set<String> myHeavyProcesses = new THashSet<String>();
  private final EventDispatcher<HeavyProcessListener> myEventDispatcher = EventDispatcher.create(HeavyProcessListener.class);

  private final EventDispatcher<HeavyProcessListener> myUIProcessDispatcher = EventDispatcher.create(HeavyProcessListener.class);
  private volatile Thread myUiActivityThread;
  /**
   Don't wait forever in case someone forgot to stop prioritizing before waiting for other threads to complete
   wait just for 12 seconds; this will be noticeable (and we'll get 2 thread dumps) but not fatal
   */
  private static final int MAX_PRIORITIZATION_MILLIS = 12 * 1000;
  private volatile long myPrioritizingStarted;

  private final List<Runnable> toExecuteOutOfHeavyActivity = new ArrayList<Runnable>();

  private HeavyProcessLatch() {
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

  private void processFinished(@NotNull String operationName) {
    synchronized (myHeavyProcesses) {
      myHeavyProcesses.remove(operationName);
    }
    myEventDispatcher.getMulticaster().processFinished();
    List<Runnable> toRunNow;
    synchronized (myHeavyProcesses) {
      if (isRunning()) {
        toRunNow = Collections.emptyList();
      }
      else {
        toRunNow = new ArrayList<Runnable>(toExecuteOutOfHeavyActivity);
        toExecuteOutOfHeavyActivity.clear();
      }
    }
    for (Runnable runnable : toRunNow) {
      try {
        runnable.run();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
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

  public void addListener(@NotNull HeavyProcessListener listener,
                          @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  public void addUIActivityListener(@NotNull HeavyProcessListener listener,
                                    @NotNull Disposable parentDisposable) {
    myUIProcessDispatcher.addListener(listener, parentDisposable);
  }

  public void executeOutOfHeavyProcess(@NotNull Runnable runnable) {
    boolean runNow;
    synchronized (myHeavyProcesses) {
      if (isRunning()) {
        runNow = false;
        toExecuteOutOfHeavyActivity.add(runnable);
      }
      else {
        runNow = true;
      }
    }
    if (runNow) {
      runnable.run();
    }
  }

  /**
   * Gives current event processed on Swing thread higher priority
   * by letting other threads to sleep a bit whenever they call checkCanceled.
   * @see #stopThreadPrioritizing()
   */
  public void prioritizeUiActivity() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    if (!Registry.is("ide.prioritize.ui.thread", false)) {
      return;
    }

    myPrioritizingStarted = System.currentTimeMillis();

    myUiActivityThread = Thread.currentThread();
    myUIProcessDispatcher.getMulticaster().processStarted();
  }

  /**
   * Removes priority from Swing thread, if present. Should be invoked before a thread starts waiting for other threads in idle mode,
   * to ensure those other threads complete ASAP.
   * @see #prioritizeUiActivity()
   */
  public void stopThreadPrioritizing() {
    if (myUiActivityThread == null) return;

    myUiActivityThread = null;
    myUIProcessDispatcher.getMulticaster().processFinished();
  }

  /**
   * @return whether there is a prioritized thread, but not the current one
   */
  public boolean isInsideLowPriorityThread() {
    Thread uiThread = myUiActivityThread;
    if (uiThread != null && uiThread != Thread.currentThread()) {
      Thread.State state = uiThread.getState();
      if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING || state == Thread.State.BLOCKED) {
        return false;
      }

      long time = System.currentTimeMillis() - myPrioritizingStarted;
      if (time < 5) {
        return false; // don't sleep when EDT activities are very short (e.g. empty processing of mouseMoved events)
      }

      if (time > MAX_PRIORITIZATION_MILLIS) {
        stopThreadPrioritizing();
        return false;
      }
      return true;
    }
    return false;
  }

  /**
   * @return whether there is a prioritized thread currently
   */
  public boolean hasPrioritizedThread() {
    return myUiActivityThread != null;
  }
}