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
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TestsProgressAnimator implements Runnable, Disposable {
  private static final int FRAMES_COUNT = 8;
  private static final int MOVIE_TIME = 800;
  private static final int FRAME_TIME = MOVIE_TIME / FRAMES_COUNT;

  public static final Icon[] FRAMES = new Icon[FRAMES_COUNT];

  private long myLastInvocationTime = -1;

  private Alarm myAlarm;
  private AbstractTestProxy myCurrentTestCase;
  private AbstractTestTreeBuilder myTreeBuilder;

  public TestsProgressAnimator(AbstractTestTreeBuilder builder) {
    Disposer.register(builder, this);
    init(builder);
  }

  static {
    FRAMES[0] = AllIcons.RunConfigurations.TestInProgress1;
    FRAMES[1] = AllIcons.RunConfigurations.TestInProgress2;
    FRAMES[2] = AllIcons.RunConfigurations.TestInProgress3;
    FRAMES[3] = AllIcons.RunConfigurations.TestInProgress4;
    FRAMES[4] = AllIcons.RunConfigurations.TestInProgress5;
    FRAMES[5] = AllIcons.RunConfigurations.TestInProgress6;
    FRAMES[6] = AllIcons.RunConfigurations.TestInProgress7;
    FRAMES[7] = AllIcons.RunConfigurations.TestInProgress8;
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

  public void setCurrentTestCase(@Nullable final AbstractTestProxy currentTestCase) {
    myCurrentTestCase = currentTestCase;
    scheduleRepaint();
  }

  public void stopMovie() {
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
    if (myTreeBuilder != null && myCurrentTestCase != null) {
      myTreeBuilder.repaintWithParents(myCurrentTestCase);
    }
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
