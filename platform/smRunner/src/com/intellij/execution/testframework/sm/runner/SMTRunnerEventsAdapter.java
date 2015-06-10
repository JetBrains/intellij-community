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
package com.intellij.execution.testframework.sm.runner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
*/
public class SMTRunnerEventsAdapter implements SMTRunnerEventsListener {
  public void onTestingStarted(@NotNull SMTestProxy.SMRootTestProxy testsRoot){}
  public void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot){}
  public void onTestsCountInSuite(final int count) {}

  public void onTestStarted(@NotNull final SMTestProxy test) {}
  public void onTestFinished(@NotNull final SMTestProxy test) {}
  public void onTestFailed(@NotNull final SMTestProxy test) {}
  public void onTestIgnored(@NotNull final SMTestProxy test) {}

  public void onSuiteStarted(@NotNull final SMTestProxy suite) {}
  public void onSuiteFinished(@NotNull final SMTestProxy suite) {}

  // Custom progress status

  public void onCustomProgressTestsCategory(@Nullable String categoryName, final int testCount) {}
  public void onCustomProgressTestStarted() {}
  public void onCustomProgressTestFailed() {}
  public void onCustomProgressTestFinished() {}

  @Override public void onSuiteTreeNodeAdded(SMTestProxy testProxy) {}
  @Override public void onSuiteTreeStarted(SMTestProxy suite) {}
}
