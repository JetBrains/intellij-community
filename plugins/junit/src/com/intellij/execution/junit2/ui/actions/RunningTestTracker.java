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

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.events.StateChangedEvent;
import com.intellij.execution.junit2.events.TestEvent;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkPropertyListener;
import com.intellij.execution.testframework.actions.TestFrameworkActions;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class RunningTestTracker extends JUnitAdapter implements TestFrameworkPropertyListener<Boolean> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.actions.TrackRunningTestAction");

  private final JUnitRunningModel myModel;
  private TrackingPolicy myTrackingPolicy;
  private TestProxy myLastRan = null;
  private TestProxy myLastSelected = null;

  private RunningTestTracker(final JUnitRunningModel model) {
    myModel = model;
    final JTree tree = myModel.getTree();
    final MouseAdapter userSelectionListener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        setUserSelection(tree.getPathForLocation(e.getX(), e.getY()));
      }
    };
    tree.addMouseListener(userSelectionListener);
    final KeyAdapter keyAdapter = new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_RIGHT) {
          setUserSelection(tree.getSelectionPath());
        }
      }
    };
    tree.addKeyListener(keyAdapter);
    Disposer.register(myModel, new Disposable() {
      @Override
      public void dispose() {
        tree.removeMouseListener(userSelectionListener);
        tree.removeKeyListener(keyAdapter);
      }
    });
    choosePolicy();
  }

  private void setUserSelection(TreePath treePath) {
    if (treePath != null) {
      final Object component = treePath.getLastPathComponent();
      if (component instanceof DefaultMutableTreeNode) {
        final Object userObject = ((DefaultMutableTreeNode)component).getUserObject();
        if (userObject instanceof NodeDescriptor) {
          myLastSelected = (TestProxy)((NodeDescriptor)userObject).getElement();
        }
      }
    }
  }

  public void onChanged(final Boolean value) {
    choosePolicy();
    myTrackingPolicy.apply();
  }

  public void onTestChanged(final TestEvent event) {
    if (event instanceof StateChangedEvent) {
      final TestProxy proxy = event.getSource();
      if (proxy == myLastRan && !isRunningState(proxy)) {
        if (myLastSelected == proxy){
          myLastSelected = null;
        }
        myLastRan = null;
      }
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
      if (myLastRan != null && isRunningState(myLastRan)) {
        if (myLastSelected == null) {
          myModel.selectTest(myLastRan);
        }
        else {
          myModel.expandTest(myLastRan);
        }
      }
    }
  };
}
