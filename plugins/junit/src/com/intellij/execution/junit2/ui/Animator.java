package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.StateChangedEvent;
import com.intellij.execution.junit2.TestEvent;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.model.StateEvent;
import com.intellij.execution.junit2.ui.model.TestTreeBuilder;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import com.intellij.util.Alarm;

import javax.swing.*;

public class Animator implements Runnable {
  private static final int FRAMES_COUNT = 8;
  private static final Icon[] ourFrames = new Icon[FRAMES_COUNT];
  private static final int MOVIE_TIME = 800;
  private static final int FRAME_TIME = MOVIE_TIME / FRAMES_COUNT;

  private TestTreeBuilder myTreeBuilder;
  private TestProxy myCurrentTestCase;
  private Alarm myAlarm;
  private long myLastInvocationTime = -1;
  public static final Icon PAUSED_ICON = TestsUIUtil.loadIcon("testPaused");

  static {
    for (int i = 0; i < FRAMES_COUNT; i++)
      ourFrames[i] = TestsUIUtil.loadIcon("testInProgress" + (i + 1));
  }

  public void setCurrentTestCase(final TestProxy currentTestCase) {
    myCurrentTestCase = currentTestCase;
    reinvoke();
  }

  public void run() {
    if (myCurrentTestCase != null) {
      final long time = System.currentTimeMillis();
      if (time - myLastInvocationTime >= FRAME_TIME) {
        updateCurrent();
        myLastInvocationTime = time;
      }
    }
    reinvoke();
  }

  private void updateCurrent() {
    myTreeBuilder.repaintWithParents(myCurrentTestCase);
  }

  private void reinvoke() {
    if (myAlarm == null) return;
    myAlarm.cancelAllRequests();
    if (myCurrentTestCase != null)
      myAlarm.addRequest(this, FRAME_TIME);
  }

  public static Icon getCurrentFrame() {
    final int frameIndex = (int) ((System.currentTimeMillis() % MOVIE_TIME) / FRAME_TIME);
    return ourFrames[frameIndex];
  }

  public void setModel(final JUnitRunningModel model) {
    myAlarm = new Alarm();
    myTreeBuilder = model.getTreeBuilder();
    model.addListener(new JUnitAdapter() {
      public void onTestChanged(final TestEvent event) {
        if (event instanceof StateChangedEvent) {
          final TestProxy test = event.getSource();
          if (test.isLeaf() && test.getState().getMagnitude() == PoolOfTestStates.RUNNING_INDEX)
            setCurrentTestCase(test);
        }
      }

      public void onRunnerStateChanged(final StateEvent event) {
        if (!event.isRunning())
          stopMovie();
      }

      public void doDispose() {
        setCurrentTestCase(null);
        disposeAlarm();
      }
    });
  }

  private void disposeAlarm() {
    if (myAlarm != null)
      myAlarm.cancelAllRequests();
    myAlarm = null;
  }

  private void stopMovie() {
    if (myCurrentTestCase != null)
      updateCurrent();
    setCurrentTestCase(null);
    disposeAlarm();
  }
}
