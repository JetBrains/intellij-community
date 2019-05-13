/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestTreeView;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fixture for the tree widget, on the left hand side of "Run" window (when running tests).
 */
public class UnitTestTreeFixture {
  private final ExecutionToolWindowFixture.ContentFixture myContentFixture;
  private final TestTreeView myTreeView;

  public UnitTestTreeFixture(@NotNull ExecutionToolWindowFixture.ContentFixture contentFixture,
                             @NotNull TestTreeView treeView) {
    myContentFixture = contentFixture;
    myTreeView = treeView;
  }

  @Nullable
  public TestFrameworkRunningModel getModel() {
    Pause.pause(new Condition("Wait for the test results model.") {
      @Override
      public boolean test() {
        return myTreeView.getData(TestTreeView.MODEL_DATA_KEY.getName()) != null;
      }
    });

    return TestTreeView.MODEL_DATA_KEY.getData(myTreeView);
  }

  public boolean isAllTestsPassed() {
    return getFailingTestsCount() == 0;
  }

  public int getFailingTestsCount() {
    int count = 0;
    AbstractTestProxy root = getModel().getRoot();
    for (AbstractTestProxy test : root.getAllTests()) {
      if (test.isLeaf() && test.isDefect()) {
        count++;
      }
    }
    return count;
  }

  public int getAllTestsCount() {
    int count = 0;
    AbstractTestProxy root = getModel().getRoot();
    for (AbstractTestProxy test : root.getAllTests()) {
      if (test.isLeaf()) {
        count++;
      }
    }
    return count;
  }

  @NotNull
  public ExecutionToolWindowFixture.ContentFixture getContent() {
    return myContentFixture;
  }
}
