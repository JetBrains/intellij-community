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
package com.intellij.execution.testframework.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;

import javax.swing.*;

public abstract class TestsProgressAnimator implements Runnable, Disposable {
  private static final int FRAMES_COUNT = 8;
  private static final int MOVIE_TIME = 800;
  private static final int FRAME_TIME = MOVIE_TIME / FRAMES_COUNT;

  public static final Icon PAUSED_ICON = TestsUIUtil.loadIcon("testPaused");
  public static final Icon[] FRAMES = new Icon[FRAMES_COUNT];

  private long myLastInvocationTime = -1;

  private Alarm myAlarm;
  private AbstractTestProxy myCurrentTestCase;
  private AbstractTestTreeBuilder myTreeBuilder;

  protected TestsProgressAnimator(Disposable parentDisposable) {
    Disposer.register(parentDisposable, this);
  }

  static {
    for (int i = 0; i < FRAMES_COUNT; i++)
      FRAMES[i] = TestsUIUtil.loadIcon("testInProgress" + (i + 1));
  }

  public static int getCurrentFrameIndex() {
    return (int) ((System.currentTimeMillis() % MOVIE_TIME) / FRAME_TIME);
  }

  public static Icon getCurrentFrame() {
    return FRAMES[getCurrentFrameIndex()];
  }

  /**
   * Initializes animator: creates alarm and sets tree builder
   * @param treeBuilder tree builder
   */
  protected void init(final AbstractTestTreeBuilder treeBuilder) {
    myAlarm = new Alarm();
    myTreeBuilder = treeBuilder;
  }

  public AbstractTestProxy getCurrentTestCase() {
    return myCurrentTestCase;
  }

  public void run() {
    if (myCurrentTestCase != null) {
      final long time = System.currentTimeMillis();
      // optimization:
      // we shouldn't repaint if this frame was painted in current interval
      if (time - myLastInvocationTime >= FRAME_TIME) {
        repaintSubTree();
        myLastInvocationTime = time;
      }
    }
    scheduleRepaint();
  }

  public void setCurrentTestCase(final AbstractTestProxy currentTestCase) {
    myCurrentTestCase = currentTestCase;
    scheduleRepaint();
  }

  public void stopMovie() {
    if (myCurrentTestCase != null)
      repaintSubTree();
    setCurrentTestCase(null);
    cancelAlarm();
  }


  public void dispose() {
    myTreeBuilder = null;
    myCurrentTestCase = null;
    cancelAlarm();
  }

  private void cancelAlarm() {
    if (myAlarm != null) {
      myAlarm.cancelAllRequests();
      myAlarm = null;
    }
  }

  private void repaintSubTree() {
    myTreeBuilder.repaintWithParents(myCurrentTestCase);
  }

  private void scheduleRepaint() {
    if (myAlarm == null) {
      return;
    }
    myAlarm.cancelAllRequests();
    if (myCurrentTestCase != null) {
      myAlarm.addRequest(this, FRAME_TIME);
    }
  }

}
