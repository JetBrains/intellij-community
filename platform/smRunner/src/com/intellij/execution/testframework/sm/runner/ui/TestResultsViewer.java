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
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public interface TestResultsViewer extends Disposable {
  /**
   * Fake Root for toplevel test suits/tests
   *
   * @return root
   */
  SMTestProxy getTestsRootNode();

  /**
   * Selects test or suite in Tests tree and notify about selection changed
   *
   * @param proxy
   */
  void selectAndNotify(@Nullable AbstractTestProxy proxy);

  void addEventsListener(EventsListener listener);

  interface EventsListener extends TestProxyTreeSelectionListener {
    void onTestingStarted(@NotNull TestResultsViewer sender);

    void onTestingFinished(@NotNull TestResultsViewer sender);

    void onTestNodeAdded(@NotNull TestResultsViewer sender, @NotNull SMTestProxy test);
  }

  class SMEventsAdapter implements EventsListener {

    @Override
    public void onTestingStarted(@NotNull TestResultsViewer sender) {
    }

    @Override
    public void onTestingFinished(@NotNull TestResultsViewer sender) {
    }

    @Override
    public void onTestNodeAdded(@NotNull TestResultsViewer sender, @NotNull SMTestProxy test) {
    }

    @Override
    public void onSelected(@Nullable SMTestProxy selectedTestProxy,
                           @NotNull TestResultsViewer viewer,
                           @NotNull TestFrameworkRunningModel model) {
    }
  }
}
