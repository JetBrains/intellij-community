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
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

public class SMTRunnerIntegrationTest extends LightPlatformTestCase {

  private static final String TEST_FRAMEWORK_NAME = "SMRunnerTests";

  private SMTRunnerConsoleView myConsole;

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
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myConsole);
    }
    finally {
      super.tearDown();
    }
  }

  public void testMultiTestings() {
    SMTestRunnerResultsForm resultsViewer = myConsole.getResultsViewer();
    SMTestProxy.SMRootTestProxy rootNode = resultsViewer.getTestsRootNode();
    ProcessHandler processHandler = new NopProcessHandler();
    myConsole.attachToProcess(processHandler);
    processHandler.startNotify();
    notifyStdoutLineAvailable(processHandler, "##teamcity[enteredTheMatrix]");
    notifyStdoutLineAvailable(processHandler, "##teamcity[testingStarted]");
    assertEquals(0, rootNode.getChildren().size());
    notifyStdoutLineAvailable(processHandler, "##teamcity[testSuiteStarted nodeId='1' parentNodeId='0' name='sum-test.js']");
    assertEquals(1, rootNode.getChildren().size());
    assertEquals(0, resultsViewer.getFinishedTestCount());
    notifyStdoutLineAvailable(processHandler, "##teamcity[testStarted nodeId='2' parentNodeId='1' name='x']");
    notifyStdoutLineAvailable(processHandler, "##teamcity[testFinished nodeId='2' duration='2']");
    assertEquals(1, resultsViewer.getFinishedTestCount());
    notifyStdoutLineAvailable(processHandler, "##teamcity[testSuiteFinished nodeId='1']");
    notifyStdoutLineAvailable(processHandler, "##teamcity[testingFinished]");
    assertEquals(1, rootNode.getChildren().size());
    notifyStdoutLineAvailable(processHandler, "##teamcity[testingStarted]");
    assertEquals(0, rootNode.getChildren().size());
    assertEquals(0, resultsViewer.getFinishedTestCount());
    processHandler.destroyProcess();
  }

  private static void notifyStdoutLineAvailable(@NotNull ProcessHandler processHandler, @NotNull String line) {
    processHandler.notifyTextAvailable(line + "\n", ProcessOutputTypes.STDOUT);
  }
}
