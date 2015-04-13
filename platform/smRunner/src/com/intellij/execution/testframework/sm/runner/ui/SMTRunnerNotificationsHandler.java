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

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerNotificationsHandler extends SMTRunnerEventsAdapter {
  private final TestConsoleProperties myConsoleProperties;
  private boolean myStarted = false;

  public SMTRunnerNotificationsHandler(@NotNull final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  public void onTestingStarted(@NotNull SMTestProxy.SMRootTestProxy testsRoot) {
    myStarted = true;
  }

  public void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot) {
    if (testsRoot.isEmptySuite() &&
        myConsoleProperties instanceof SMTRunnerConsoleProperties &&
        ((SMTRunnerConsoleProperties)myConsoleProperties).fixEmptySuite()) {
      return;
    }
    TestsUIUtil.notifyByBalloon(myConsoleProperties.getProject(), myStarted, testsRoot, myConsoleProperties, null);
  }
}