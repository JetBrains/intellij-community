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

import com.intellij.execution.junit2.events.NewChildEvent;
import com.intellij.execution.junit2.events.TestEvent;
import com.intellij.execution.junit2.TestProxy;

public class TreeCollapser extends JUnitAdapter {
  private JUnitRunningModel myModel;
  private TestProxy myLastDynamicSuite = null;

  public void setModel(final JUnitRunningModel model) {
    myModel = model;
    model.addListener(this);
  }

  public void onTestChanged(final TestEvent event) {
    if (!(event instanceof NewChildEvent))
      return;
    final TestProxy parent = event.getSource();
    if (parent == myLastDynamicSuite)
      return;
    if (parent.getParent() != myModel.getRoot())
      return;
    if (myLastDynamicSuite != null && myLastDynamicSuite.getState().isPassed())
      myModel.collapse(myLastDynamicSuite);
    myLastDynamicSuite = parent;
  }

  public void doDispose() {
    myModel = null;
    myLastDynamicSuite = null;
  }
}
