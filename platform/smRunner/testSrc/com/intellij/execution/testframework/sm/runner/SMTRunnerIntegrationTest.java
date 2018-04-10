/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class SMTRunnerIntegrationTest extends LightPlatformTestCase {

  private static final String TEST_FRAMEWORK_NAME = "SMRunnerTests";

  private SMTRunnerConsoleView myConsole;
  private SMTestRunnerResultsForm myResultsViewer;
  private SMTestProxy.SMRootTestProxy myRootNode;
  private ProcessHandler myProcessHandler;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    SMTRunnerConsoleProperties properties = new SMTRunnerConsoleProperties(new MockRuntimeConfiguration(getProject()),
                                                                           TEST_FRAMEWORK_NAME,
                                                                           DefaultRunExecutor.getRunExecutorInstance()) {
      @Override
      public boolean isIdBasedTestTree() {
        return true;
      }
    };
    myConsole = (SMTRunnerConsoleView)SMTestRunnerConnectionUtil.createConsole(TEST_FRAMEWORK_NAME, properties);
    myResultsViewer = myConsole.getResultsViewer();
    myRootNode = myResultsViewer.getTestsRootNode();
    myProcessHandler = new NopProcessHandler();
    myConsole.attachToProcess(myProcessHandler);
    myProcessHandler.startNotify();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myProcessHandler.destroyProcess();
      Disposer.dispose(myConsole);
    }
    finally {
      super.tearDown();
    }
  }

  public void multiTestings() {
    notifyStdoutLineAvailable("##teamcity[enteredTheMatrix]");
    notifyStdoutLineAvailable("##teamcity[testingStarted]");
    assertState(0, 0, 0, ColorProgressBar.GREEN);
    notifyStdoutLineAvailable("##teamcity[testSuiteStarted nodeId='1' parentNodeId='0' name='sum-test.js']");
    assertState(0, 0, 1, ColorProgressBar.GREEN);
    notifyStdoutLineAvailable("##teamcity[testStarted nodeId='2' parentNodeId='1' name='x']");
    notifyStdoutLineAvailable("##teamcity[testFinished nodeId='2' duration='2']");
    assertState(1, 0, 1, ColorProgressBar.GREEN);
    notifyStdoutLineAvailable("##teamcity[testSuiteFinished nodeId='1']");
    notifyStdoutLineAvailable("##teamcity[testingFinished]");
    assertState(1, 0, 1, ColorProgressBar.GREEN);
    notifyStdoutLineAvailable("##teamcity[testingStarted]");
    assertState(0, 0, 0, ColorProgressBar.GREEN);
  }

  public void failedThenSuccessTestings() {
    notifyStdoutLineAvailable("##teamcity[enteredTheMatrix]");
    notifyStdoutLineAvailable("##teamcity[testingStarted]");
    notifyStdoutLineAvailable("##teamcity[testSuiteStarted nodeId='1' parentNodeId='0' name='sum-test.js']");
    assertState(0, 0, 1, ColorProgressBar.GREEN);
    notifyStdoutLineAvailable("##teamcity[testStarted nodeId='2' parentNodeId='1' name='x']");
    assertState(0, 0, 1, ColorProgressBar.GREEN);
    notifyStdoutLineAvailable("##teamcity[testFailed nodeId='2' message='Failed' duration='2']");
    assertState(1, 1, 1, ColorProgressBar.RED);
    notifyStdoutLineAvailable("##teamcity[testSuiteFinished nodeId='1']");
    notifyStdoutLineAvailable("##teamcity[testingFinished]");
    assertState(1, 1, 1, ColorProgressBar.RED);
    notifyStdoutLineAvailable("##teamcity[testingStarted]");
    assertState(0, 0, 0, ColorProgressBar.GREEN);

    notifyStdoutLineAvailable("##teamcity[testSuiteStarted nodeId='1' parentNodeId='0' name='sum-test.js']");
    notifyStdoutLineAvailable("##teamcity[testStarted nodeId='2' parentNodeId='1' name='x']");
    notifyStdoutLineAvailable("##teamcity[testFinished nodeId='2' duration='2']");
    notifyStdoutLineAvailable("##teamcity[testSuiteFinished nodeId='1']");
    notifyStdoutLineAvailable("##teamcity[testingFinished]");
    assertState(1, 0, 1, ColorProgressBar.GREEN);
  }

  private void assertState(int finishedTests, int failedTests, int topLevelChildrenCount, @NotNull Color statusColor) {
    assertEquals(finishedTests, myResultsViewer.getFinishedTestCount());
    assertEquals(failedTests, myResultsViewer.getFailedTestCount());
    assertEquals(topLevelChildrenCount, myRootNode.getChildren().size());
    assertEquals(statusColor, myResultsViewer.getTestsStatusColor());
  }

  private void notifyStdoutLineAvailable(@NotNull String line) {
    myProcessHandler.notifyTextAvailable(line + "\n", ProcessOutputTypes.STDOUT);
  }
}
