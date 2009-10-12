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

import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.model.StateEvent;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.actions.TestFrameworkActions;

public class JUnitActions extends TestFrameworkActions {
  public static void installAutoscrollToFirstDefect(final JUnitRunningModel model) {
    model.addListener(new JUnitAdapter() {
      public void onRunnerStateChanged(final StateEvent event) {
        if (event.isRunning() || !shouldSelect())
          return;
        final AbstractTestProxy firstDefect = Filter.DEFECTIVE_LEAF.detectIn(model.getRoot().getAllTests());
        if (firstDefect != null) model.selectAndNotify(firstDefect);
      }

      private boolean shouldSelect() {
        return JUnitConsoleProperties.SELECT_FIRST_DEFECT.value(model.getProperties());
      }
    });
  }
}
