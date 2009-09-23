package com.intellij.openapi.vcs.changes;

import com.intellij.lifecycle.ControlledAlarmFactory;
import com.intellij.lifecycle.SlowlyClosingAlarm;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.util.Alarm;

import java.util.concurrent.atomic.AtomicBoolean;

public class ControlledCycle implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ControlledCycle");

  private final Alarm mySimpleAlarm;
  private final SlowlyClosingAlarm myControlledAlarm;
  // this interval is also to check for not initialized paths, so it is rather small
  private static final int ourRefreshInterval = 10000;
  private final Runnable myRunnable;

  private final AtomicBoolean myActive;

  public ControlledCycle(final Project project, final Getter<Boolean> runnable) {
    myActive = new AtomicBoolean(false);
    myRunnable = new Runnable() {
      boolean shouldBeContinued = true;
      public void run() {
        try {
          shouldBeContinued = Boolean.TRUE.equals(runnable.get());
        } catch (ProcessCanceledException e) {
          //
        } catch (RuntimeException e) {
          LOG.info(e);
        }
        if (! shouldBeContinued) {
          myActive.set(false);
        } else {
          mySimpleAlarm.addRequest(ControlledCycle.this, ourRefreshInterval);
        }
      }
    };
    mySimpleAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, project);
    myControlledAlarm = ControlledAlarmFactory.createOnApplicationPooledThread(project);
  }

  public void start() {
    final boolean wasSet = myActive.compareAndSet(false, true);
    if (wasSet) {
      mySimpleAlarm.addRequest(this, ourRefreshInterval);
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
}
