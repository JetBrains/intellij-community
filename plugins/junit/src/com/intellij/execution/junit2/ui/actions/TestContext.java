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
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;

public class TestContext {
  public static final DataKey<TestContext> DATA_KEY = DataKey.create("JUNIT_CONTEXT");
  @Deprecated public static final String TEST_CONTEXT = DATA_KEY.getName();

  private final JUnitRunningModel myModel;
  private final TestProxy mySelection;

  public TestContext(final JUnitRunningModel model, final TestProxy selection) {
    myModel = model;
    mySelection = selection;
  }

  public JUnitRunningModel getModel() {
    return myModel;
  }

  public TestProxy getSelection() {
    return mySelection;
  }

  public boolean hasSelection() {
    return getSelection() != null && getModel() != null;
  }

  public boolean treeContainsSelection() {
    return getModel().hasInTree(getSelection());
  }

  public static TestContext from(final AnActionEvent event) {
    return DATA_KEY.getData(event.getDataContext());
  }
}
