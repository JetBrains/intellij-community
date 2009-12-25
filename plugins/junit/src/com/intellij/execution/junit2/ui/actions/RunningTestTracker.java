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

package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.junit2.events.StateChangedEvent;
import com.intellij.execution.junit2.events.TestEvent;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkPropertyListener;
import com.intellij.execution.testframework.actions.TestFrameworkActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;

class RunningTestTracker extends JUnitAdapter implements TestFrameworkPropertyListener<Boolean> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.actions.TrackRunningTestAction");

  private final JUnitRunningModel myModel;
  private TrackingPolicy myTrackingPolicy;
  private TestProxy myLastRan = null;

  private RunningTestTracker(final JUnitRunningModel model) {
    myModel = model;
    choosePolicy();
  }

  public void onChanged(final Boolean value) {
    choosePolicy();
    myTrackingPolicy.apply();
  }

  public void onTestChanged(final TestEvent event) {
    if (event instanceof StateChangedEvent) {
      final TestProxy proxy = event.getSource();
      if (proxy == myLastRan && !isRunningState(proxy)) myLastRan = null;
      if (proxy.isLeaf() && isRunningState(proxy)) myLastRan = proxy;
      myTrackingPolicy.applyTo(proxy);
    }
  }

  public static void install(final JUnitRunningModel model) {
    final RunningTestTracker testTracker = new RunningTestTracker(model);
    model.addListener(testTracker);
    TestFrameworkActions.addPropertyListener(JUnitConsoleProperties.TRACK_RUNNING_TEST, testTracker, model, false);
  }

  private static boolean isRunningState(final TestProxy test) {
    return test.getState().getMagnitude() == PoolOfTestStates.RUNNING_INDEX;
  }

  private abstract static class TrackingPolicy {
    protected abstract void applyTo(TestProxy test);
    protected abstract void apply();
  }

  private void choosePolicy() {
    final boolean shouldTrack = JUnitConsoleProperties.TRACK_RUNNING_TEST.value(myModel.getProperties());
    myTrackingPolicy = shouldTrack ? TRACK_RUNNING : DONT_TRACK;
  }

  private static final TrackingPolicy DONT_TRACK = new TrackingPolicy() {
    protected void applyTo(final TestProxy test) {}
    protected void apply() {}
  };

  private final TrackingPolicy TRACK_RUNNING = new TrackingPolicy() {
    protected void applyTo(final TestProxy test) {
      LOG.assertTrue(myModel != null);
      selectLastTest();
      if (!test.isLeaf() && test.getState().isPassed())
        myModel.collapse(test);
    }

    protected void apply() {
      LOG.assertTrue(myModel != null);
      selectLastTest();
    }

    private void selectLastTest() {
      if (myLastRan != null && isRunningState(myLastRan))
        myModel.selectTest(myLastRan);
    }
  };
}
