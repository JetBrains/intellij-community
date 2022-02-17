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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@Deprecated(forRemoval = true)
public class TestsProgressAnimator implements Runnable, Disposable {
  private static final int FRAMES_COUNT = 8;
  private static final int MOVIE_TIME = 800;
  private static final int FRAME_TIME = MOVIE_TIME / FRAMES_COUNT;

  public static final Icon[] FRAMES = new Icon[FRAMES_COUNT];

  private long myLastInvocationTime = -1;

  private final Alarm myAlarm = new Alarm();
  private volatile AbstractTestProxy myCurrentTestCase;
  private final AbstractTestTreeBuilderBase myTreeBuilder;

  /**
   * @deprecated To be deleted when AbstractTreeBuilder would be completely eliminated
   */
  @Deprecated(forRemoval = true)
  public TestsProgressAnimator(AbstractTestTreeBuilder builder) {
    this((AbstractTestTreeBuilderBase)builder);
  }

  public TestsProgressAnimator(AbstractTestTreeBuilderBase builder) {
    Disposer.register(builder, this);
    myTreeBuilder = builder;
  }

  static {
    FRAMES[0] = AllIcons.Process.Step_1;
    FRAMES[1] = AllIcons.Process.Step_2;
    FRAMES[2] = AllIcons.Process.Step_3;
    FRAMES[3] = AllIcons.Process.Step_4;
    FRAMES[4] = AllIcons.Process.Step_5;
    FRAMES[5] = AllIcons.Process.Step_6;
    FRAMES[6] = AllIcons.Process.Step_7;
    FRAMES[7] = AllIcons.Process.Step_8;
  }

  public static int getCurrentFrameIndex() {
    return (int) ((System.currentTimeMillis() % MOVIE_TIME) / FRAME_TIME);
  }

  public static Icon getCurrentFrame() {
    return FRAMES[getCurrentFrameIndex()];
  }

  public AbstractTestProxy getCurrentTestCase() {
    return myCurrentTestCase;
  }

  @Override
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

  //called from output reader thread
  public void setCurrentTestCase(@Nullable final AbstractTestProxy currentTestCase) {
    myCurrentTestCase = currentTestCase;
    scheduleRepaint();
  }

  public void stopMovie() {
    myCurrentTestCase = null;
    myAlarm.cancelAllRequests();
  }


  @Override
  public synchronized void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myCurrentTestCase = null;
    Disposer.dispose(myAlarm);
  }

  private void repaintSubTree() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    AbstractTestProxy testProxy = myCurrentTestCase;
    if (testProxy != null) {
      myTreeBuilder.repaintWithParents(testProxy);
    }
  }

  private synchronized void scheduleRepaint() {
    myAlarm.cancelAllRequests();
    if (myCurrentTestCase != null && !myAlarm.isDisposed()) {
      myAlarm.addRequest(this, FRAME_TIME);
    }
  }

}
