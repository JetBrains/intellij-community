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

import com.intellij.lifecycle.AtomicSectionsAware;
import com.intellij.lifecycle.ControlledAlarmFactory;
import com.intellij.lifecycle.SlowlyClosingAlarm;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class ControlledCycle implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ControlledCycle");

  private final Alarm mySimpleAlarm;
  private final SlowlyClosingAlarm myControlledAlarm;
  // this interval is also to check for not initialized paths, so it is rather small
  private static final int ourRefreshInterval = 10000;
  private final int myRefreshInterval;
  private final Runnable myRunnable;

  private final AtomicBoolean myActive;

  public ControlledCycle(final Project project, final MyCallback callback, @NotNull final String name) {
    this(project, callback, name, -1);
  }

  public ControlledCycle(final Project project, final MyCallback callback, @NotNull final String name, final int refreshInterval) {
    myRefreshInterval = (refreshInterval <= 0) ? ourRefreshInterval : refreshInterval;
    myActive = new AtomicBoolean(false);
    myRunnable = new Runnable() {
      boolean shouldBeContinued = true;
      public void run() {
        try {
          shouldBeContinued = callback.call(myControlledAlarm);
        } catch (ProcessCanceledException e) {
          return;
        } catch (RuntimeException e) {
          LOG.info(e);
        }
        if (! shouldBeContinued) {
          myActive.set(false);
        } else {
          mySimpleAlarm.addRequest(ControlledCycle.this, myRefreshInterval);
        }
      }
    };
    mySimpleAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, project);
    myControlledAlarm = ControlledAlarmFactory.createOnApplicationPooledThread(project, name);
  }

  public void start() {
    final boolean wasSet = myActive.compareAndSet(false, true);
    if (wasSet) {
      mySimpleAlarm.addRequest(this, myRefreshInterval);
    }
  }

  public void run() {
    try {
      myControlledAlarm.checkShouldExit();
      myControlledAlarm.addRequest(myRunnable);
    } catch (ProcessCanceledException e) {
      //
    }
  }

  public interface MyCallback {
    boolean call(final AtomicSectionsAware atomicSectionsAware);
  }
}
