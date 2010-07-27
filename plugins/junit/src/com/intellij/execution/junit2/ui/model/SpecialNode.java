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

package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.junit2.ui.TestProgress;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.states.NotFailedState;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class SpecialNode extends TestProxy {
  public static final String ALL_PASSED = ExecutionBundle.message("junit.all.tests.passed.label");
  public static final String TESTS_IN_PROGRESS = ExecutionBundle.message("junit.tests.in.progress.label");

  private final JUnitRunningModel myModel;
  private final AbstractTreeBuilder myBuilder;

  private boolean myIsVisible = false;
  private CompletionEvent myCompletionEvent;

  public SpecialNode(final TestTreeBuilder treeBuilder, final JUnitRunningModel model) {
    super(new RootTestInfo());
    myModel = model;
    myBuilder = treeBuilder;
    final MyJUnitAdapter listener = new MyJUnitAdapter();
    myModel.addListener(listener);
    myModel.getProgress().addChangeListener(listener);
    setState(NotFailedState.createPassed());
  }

  public Object[] asArray() {
    return new Object[]{this};
  }

  public void setVisible(final boolean isVisible) {
    if (myIsVisible == isVisible) return;
    myIsVisible = isVisible;
    updateName();
  }

  private class MyJUnitAdapter extends JUnitAdapter implements ChangeListener {
    public void stateChanged(final ChangeEvent e) {
      if (myCompletionEvent != null) updateName();
    }

    public MyJUnitAdapter() {

    }

    public void onRunnerStateChanged(final StateEvent event) {
      if (!event.isRunning()) {
        myCompletionEvent = (CompletionEvent) event;
        updateName();
      }
    }
  }

  private void updateName() {
    if (!myIsVisible) return;
    final RootTestInfo myTestInfo = (RootTestInfo)getInfo();
    final String newName;
    final TestProgress progress = myModel.getProgress();
    if (myCompletionEvent == null) {
      newName = TESTS_IN_PROGRESS;
    }
    else if (myCompletionEvent.isNormalExit() && progress.getValue() == progress.getMaximum()) {
      newName = ALL_PASSED;
    }
    else {
      switch(myCompletionEvent.getType()) {
        case DONE:
          newName = ExecutionBundle.message("junit.runing.info.tests.in.progress.done.tree.node");
          break;
        default:
          newName = ExecutionBundle.message("junit.runing.info.tests.in.progress.terminated.tre.node");
      }
    }
    myTestInfo.setName(newName);
    myBuilder.updateFromRoot();
  }
}
