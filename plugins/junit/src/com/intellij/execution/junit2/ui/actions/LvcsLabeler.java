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

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.junit2.ui.model.CompletionEvent;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.StateEvent;
import com.intellij.execution.testframework.LvcsHelper;
import com.intellij.execution.testframework.TestFrameworkRunningModel;

public class LvcsLabeler extends JUnitAdapter {
  private final TestFrameworkRunningModel myModel;

  public LvcsLabeler(final TestFrameworkRunningModel model) {
    myModel = model;
  }

  public void onRunnerStateChanged(final StateEvent event) {
    if (!(event instanceof CompletionEvent)) {
      return;
    }
    final boolean areTestsFailed = myModel.getRoot().isDefect();
    final CompletionEvent completion = (CompletionEvent)event;
    final RunProfile configuration = myModel.getProperties().getConfiguration();
    if (configuration == null) {
      return;
    }
    if (testsTerminatedAndNotFailed(completion, areTestsFailed)) return;

    if (completion.isNormalExit()) {
      LvcsHelper.addLabel(myModel);
    }
  }


  private static boolean testsTerminatedAndNotFailed(final CompletionEvent completion, final boolean areTestsPassed) {
    return !completion.isNormalExit() && areTestsPassed;
  }
}
