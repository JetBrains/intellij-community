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

import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.ui.TestsOutputConsolePrinter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Disposer;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerConsoleTest extends BaseSMTRunnerTestCase {
  private MyConsoleView myConsole;
  private GeneralToSMTRunnerEventsConvertor myEventsProcessor;
  private MockPrinter myMockResetablePrinter;
  private SMTestProxy myRootSuite;
  private SMTestRunnerResultsForm myResultsViewer;

  private class MyConsoleView extends SMTRunnerConsoleView {
    private final TestsOutputConsolePrinter myTestsOutputConsolePrinter;

    private MyConsoleView(final TestConsoleProperties consoleProperties, final RunnerSettings runnerSettings,
                          final ConfigurationPerRunnerSettings configurationPerRunnerSettings) {
      super(consoleProperties, runnerSettings, configurationPerRunnerSettings);

      myTestsOutputConsolePrinter = new TestsOutputConsolePrinter(MyConsoleView.this, consoleProperties) {
        @Override
        public void print(final String text, final ConsoleViewContentType contentType) {
          myMockResetablePrinter.print(text, contentType);
        }
      };
    }

    @Override
    public TestsOutputConsolePrinter getPrinter() {
      return myTestsOutputConsolePrinter;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final TestConsoleProperties consoleProperties = createConsoleProperties();
    final ExecutionEnvironment environment = new ExecutionEnvironment();

    myMockResetablePrinter = new MockPrinter(true);
    myConsole = new MyConsoleView(consoleProperties, environment.getRunnerSettings(), environment.getConfigurationSettings());
    myConsole.initUI();
    myResultsViewer = myConsole.getResultsViewer();
    myRootSuite = myResultsViewer.getTestsRootNode();
    myEventsProcessor = new GeneralToSMTRunnerEventsConvertor(myResultsViewer.getTestsRootNode(), "SMTestFramework");

    myEventsProcessor.onStartTesting();
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myEventsProcessor);
    Disposer.dispose(myConsole);

    super.tearDown();
  }

  public void testPrintTestProxy() {
    mySimpleTest.setPrintLinstener(myMockResetablePrinter);
    mySimpleTest.addLast(new Printable() {
      public void printOn(final Printer printer) {
        printer.print("std out", ConsoleViewContentType.NORMAL_OUTPUT);
        printer.print("std err", ConsoleViewContentType.ERROR_OUTPUT);
        printer.print("std sys", ConsoleViewContentType.SYSTEM_OUTPUT);
      }
    });
    assertAllOutputs(myMockResetablePrinter, "std out", "std err", "std sys");
  }

  public void testAddStdOut() {
    mySimpleTest.setPrintLinstener(myMockResetablePrinter);

    mySimpleTest.addStdOutput("one", ProcessOutputTypes.STDOUT);
    assertStdOutput(myMockResetablePrinter, "one");

    mySimpleTest.addStdErr("two");
    assertStdErr(myMockResetablePrinter, "two");

    mySimpleTest.addStdOutput("one", ProcessOutputTypes.STDOUT);
    mySimpleTest.addStdOutput("one", ProcessOutputTypes.STDOUT);
    mySimpleTest.addStdErr("two");
    mySimpleTest.addStdErr("two");
    assertAllOutputs(myMockResetablePrinter, "oneone", "twotwo", "");
  }

  public void testAddStdSys() {
    mySimpleTest.setPrintLinstener(myMockResetablePrinter);

    mySimpleTest.addSystemOutput("sys");
    assertAllOutputs(myMockResetablePrinter, "", "", "sys");
  }

  public void testPrintTestProxy_Order() {
    mySimpleTest.setPrintLinstener(myMockResetablePrinter);

    sendToTestProxyStdOut(mySimpleTest, "first ");
    sendToTestProxyStdOut(mySimpleTest, "second");

    assertStdOutput(myMockResetablePrinter, "first second");
  }

  public void testSetPrintListener_ForExistingChildren() {
    mySuite.addChild(mySimpleTest);

    mySuite.setPrintLinstener(myMockResetablePrinter);

    sendToTestProxyStdOut(mySimpleTest, "child ");
    sendToTestProxyStdOut(mySuite, "root");

    assertStdOutput(myMockResetablePrinter, "child root");
  }

  public void testSetPrintListener_OnNewChild() {
    mySuite.setPrintLinstener(myMockResetablePrinter);

    sendToTestProxyStdOut(mySuite, "root ");

    sendToTestProxyStdOut(mySimpleTest, "[child old msg] ");
    mySuite.addChild(mySimpleTest);

    sendToTestProxyStdOut(mySuite, "{child added} ");
    sendToTestProxyStdOut(mySimpleTest, "[child new msg]");
    // printer for parent have been already set, thus new
    // child should immediately print himself on this printer
    assertStdOutput(myMockResetablePrinter, "root [child old msg] {child added} [child new msg]");
  }

  public void testDefferedPrint() {
    sendToTestProxyStdOut(mySimpleTest, "one ");
    sendToTestProxyStdOut(mySimpleTest, "two ");
    sendToTestProxyStdOut(mySimpleTest, "three");

    myMockResetablePrinter.onNewAvailable(mySimpleTest);
    assertStdOutput(myMockResetablePrinter, "one two three");

    myMockResetablePrinter.resetIfNecessary();
    assertFalse(myMockResetablePrinter.hasPrinted());

    myMockResetablePrinter.onNewAvailable(mySimpleTest);
    assertStdOutput(myMockResetablePrinter, "one two three");
  }

  public void testProcessor_OnTestStdOutput() {
    startTestWithPrinter("my_test");

    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stdout2", true);

    assertStdOutput(myMockResetablePrinter, "stdout1 stdout2");
  }

  public void testProcessor_OnTestStdErr() {
    startTestWithPrinter("my_test");

    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);
    myEventsProcessor.onTestOutput("my_test", "stderr2", false);

    assertStdErr(myMockResetablePrinter, "stderr1 stderr2");
  }

  public void testProcessor_OnTestMixedStd() {
    startTestWithPrinter("my_test");

    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);
    myEventsProcessor.onTestOutput("my_test", "stdout2", true);
    myEventsProcessor.onTestOutput("my_test", "stderr2", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 stdout2", "stderr1 stderr2", "");
  }

  public void testProcessor_OnFailure() {
    final SMTestProxy myTest1 = startTestWithPrinter("my_test");

    myEventsProcessor.onTestFailure("my_test", "error msg", "method1:1\nmethod2:2", false);
    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "\nerror msg\nmethod1:1\nmethod2:2\nstderr1 ", "");

    final MockPrinter mockPrinter1 = new MockPrinter(true);
    mockPrinter1.onNewAvailable(myTest1);
    assertAllOutputs(mockPrinter1, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");

    //other output order
    final SMTestProxy myTest2 = startTestWithPrinter("my_test2");
    myEventsProcessor.onTestOutput("my_test2", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test2", "stderr1 ", false);
    myEventsProcessor.onTestFailure("my_test2", "error msg", "method1:1\nmethod2:2", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
    final MockPrinter mockPrinter2 = new MockPrinter(true);
    mockPrinter2.onNewAvailable(myTest2);
    assertAllOutputs(mockPrinter2, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
  }

  public void testProcessor_OnFailure_EmptyStacktrace() {
    final SMTestProxy myTest1 = startTestWithPrinter("my_test");

    myEventsProcessor.onTestFailure("my_test", "error msg", "\n\n", false);
    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "\nerror msg\nstderr1 ", "");

    final MockPrinter mockPrinter1 = new MockPrinter(true);
    mockPrinter1.onNewAvailable(myTest1);
    assertAllOutputs(mockPrinter1, "stdout1 ", "stderr1 \nerror msg\n", "");
  }

 public void testProcessor_OnError() {
    final SMTestProxy myTest1 = startTestWithPrinter("my_test");

    myEventsProcessor.onTestFailure("my_test", "error msg", "method1:1\nmethod2:2", true);
    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "\nerror msg\nmethod1:1\nmethod2:2\nstderr1 ", "");

    final MockPrinter mockPrinter1 = new MockPrinter(true);
    mockPrinter1.onNewAvailable(myTest1);
    assertAllOutputs(mockPrinter1, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");

    //other output order
    final SMTestProxy myTest2 = startTestWithPrinter("my_test2");
    myEventsProcessor.onTestOutput("my_test2", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test2", "stderr1 ", false);
    myEventsProcessor.onTestFailure("my_test2", "error msg", "method1:1\nmethod2:2", true);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
    final MockPrinter mockPrinter2 = new MockPrinter(true);
    mockPrinter2.onNewAvailable(myTest2);
    assertAllOutputs(mockPrinter2, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
  }

 public void testProcessor_OnErrorMsg() {
    final SMTestProxy myTest1 = startTestWithPrinter("my_test");

    myEventsProcessor.onError("error msg", "method1:1\nmethod2:2");
    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "\nerror msg\nmethod1:1\nmethod2:2\nstderr1 ", "");

    final MockPrinter mockPrinter1 = new MockPrinter(true);
    mockPrinter1.onNewAvailable(myTest1);
    assertAllOutputs(mockPrinter1, "stdout1 ", "\n" +
                                               "error msg\n" +
                                               "method1:1\n" +
                                               "method2:2\n" +
                                               "stderr1 ", "");
    myEventsProcessor.onTestFinished("my_test", 1);
    myTest1.setFinished();

    //other output order
    final SMTestProxy myTest2 = startTestWithPrinter("my_test2");
    myEventsProcessor.onTestOutput("my_test2", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test2", "stderr1 ", false);
    myEventsProcessor.onError("error msg", "method1:1\nmethod2:2");

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
    final MockPrinter mockPrinter2 = new MockPrinter(true);
    mockPrinter2.onNewAvailable(myTest2);
    assertAllOutputs(mockPrinter2, "stdout1 ", "stderr1 \nerror msg\nmethod1:1\nmethod2:2\n", "");
  }

  public void testProcessor_Suite_OnErrorMsg() {
    myEventsProcessor.onError("error msg:root", "method1:1\nmethod2:2");

    myEventsProcessor.onSuiteStarted("suite", null);
    final SMTestProxy suite = myEventsProcessor.getCurrentSuite();
    suite.setPrintLinstener(myMockResetablePrinter);
    myEventsProcessor.onError("error msg:suite", "method1:1\nmethod2:2");

    assertAllOutputs(myMockResetablePrinter, "", "\n" +
                                                 "error msg:suite\n" +
                                                 "method1:1\n" +
                                                 "method2:2\n", "");

    final MockPrinter mockSuitePrinter = new MockPrinter(true);
    mockSuitePrinter.onNewAvailable(suite);
    assertAllOutputs(mockSuitePrinter, "", "\n" +
                                               "error msg:suite\n" +
                                               "method1:1\n" +
                                               "method2:2\n", "");
    final MockPrinter mockRootSuitePrinter = new MockPrinter(true);
    mockRootSuitePrinter.onNewAvailable(myRootSuite);
    assertAllOutputs(mockRootSuitePrinter, "", "\n" +
                                               "error msg:root\n" +
                                               "method1:1\n" +
                                               "method2:2\n" +
                                               "\n" +
                                               "error msg:suite\n" +
                                               "method1:1\n" +
                                               "method2:2\n", "");
  }

  public void testProcessor_OnIgnored() {
    final SMTestProxy myTest1 = startTestWithPrinter("my_test");

    myEventsProcessor.onTestIgnored("my_test", "ignored msg", null);
    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "stderr1 ", "\nignored msg\n");

    final MockPrinter mockPrinter1 = new MockPrinter(true);
    mockPrinter1.onNewAvailable(myTest1);
    assertAllOutputs(mockPrinter1, "stdout1 ", "stderr1 ", "\nignored msg\n");

    //other output order
    final SMTestProxy myTest2 = startTestWithPrinter("my_test2");
    myEventsProcessor.onTestOutput("my_test2", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test2", "stderr1 ", false);
    myEventsProcessor.onTestIgnored("my_test2", "ignored msg", null);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ", "stderr1 ", "\nignored msg\n");
    final MockPrinter mockPrinter2 = new MockPrinter(true);
    mockPrinter2.onNewAvailable(myTest2);
    assertAllOutputs(mockPrinter2, "stdout1 ", "stderr1 ", "\nignored msg\n");
  }

  public void testProcessor_OnIgnored_WithStacktrace() {
    final SMTestProxy myTest1 = startTestWithPrinter("my_test");

    myEventsProcessor.onTestIgnored("my_test", "ignored2 msg", "method1:1\nmethod2:2");
    myEventsProcessor.onTestOutput("my_test", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test", "stderr1 ", false);

    assertAllOutputs(myMockResetablePrinter, "stdout1 ",
                     "\nmethod1:1\nmethod2:2\nstderr1 ",
                     "\nignored2 msg");

    final MockPrinter mockPrinter1 = new MockPrinter(true);
    mockPrinter1.onNewAvailable(myTest1);
    assertAllOutputs(mockPrinter1,
                     "stdout1 ",
                     "stderr1 \nmethod1:1\nmethod2:2\n",
                     "\nignored2 msg");

    //other output order
    final SMTestProxy myTest2 = startTestWithPrinter("my_test2");
    myEventsProcessor.onTestOutput("my_test2", "stdout1 ", true);
    myEventsProcessor.onTestOutput("my_test2", "stderr1 ", false);
    myEventsProcessor.onTestIgnored("my_test2", "ignored msg", "method1:1\nmethod2:2");

    assertAllOutputs(myMockResetablePrinter,
                     "stdout1 ",
                     "stderr1 \nmethod1:1\nmethod2:2\n",
                     "\nignored msg");
    final MockPrinter mockPrinter2 = new MockPrinter(true);
    mockPrinter2.onNewAvailable(myTest2);
    assertAllOutputs(mockPrinter2,
                     "stdout1 ",
                     "stderr1 \nmethod1:1\nmethod2:2\n",
                     "\nignored msg");
  }

  public void testOnUncapturedOutput_BeforeProcessStarted() {
    myRootSuite.setPrintLinstener(myMockResetablePrinter);

    assertOnUncapturedOutput();
  }

  public void testOnUncapturedOutput_BeforeFirstSuiteStarted() {
    myRootSuite.setPrintLinstener(myMockResetablePrinter);

    myEventsProcessor.onStartTesting();
    assertOnUncapturedOutput();
  }

  public void testOnUncapturedOutput_SomeSuite() {
    myEventsProcessor.onStartTesting();

    myEventsProcessor.onSuiteStarted("my suite", null);
    final SMTestProxy mySuite = myEventsProcessor.getCurrentSuite();
    assertTrue(mySuite != myRootSuite);
    mySuite.setPrintLinstener(myMockResetablePrinter);

    assertOnUncapturedOutput();
  }

  public void testOnUncapturedOutput_SomeTest() {
    myEventsProcessor.onStartTesting();

    myEventsProcessor.onSuiteStarted("my suite", null);
    startTestWithPrinter("my test");

    assertOnUncapturedOutput();
  }


  public void assertOnUncapturedOutput() {
    myEventsProcessor.onUncapturedOutput("stdout", ProcessOutputTypes.STDOUT);
    myEventsProcessor.onUncapturedOutput("stderr", ProcessOutputTypes.STDERR);
    myEventsProcessor.onUncapturedOutput("system", ProcessOutputTypes.SYSTEM);

    assertAllOutputs(myMockResetablePrinter, "stdout", "stderr", "system");
  }

  public void assertStdOutput(final MockPrinter printer, final String out) {
    assertAllOutputs(printer, out, "", "");
  }

  public void assertStdErr(final MockPrinter printer, final String out) {
    assertAllOutputs(printer, "", out, "");
  }

  public void assertAllOutputs(final MockPrinter printer,
                              final String out, final String err, final String sys) {
    assertTrue(printer.hasPrinted());
    assertEquals(out, printer.getStdOut());
    assertEquals(err, printer.getStdErr());
    assertEquals(sys, printer.getStdSys());

    printer.resetIfNecessary();
  }

  public void testStopCollectingOutput() {
    myResultsViewer.selectAndNotify(myResultsViewer.getTestsRootNode());
    myConsole.attachToProcess(null);

    myEventsProcessor.onStartTesting();
    myEventsProcessor.onSuiteStarted("suite", null);
    final SMTestProxy suite = myEventsProcessor.getCurrentSuite();
    myEventsProcessor.onSuiteFinished("suite");
    myEventsProcessor.onUncapturedOutput("preved", ProcessOutputTypes.STDOUT);
    myEventsProcessor.onFinishTesting();

    //myResultsViewer.selectAndNotify(suite);
    //the string above doesn't update tree immediately so we should simulate update
    myConsole.getPrinter().updateOnTestSelected(suite);

    //Lets reset printer /clear console/ before selection changed to
    //get after selection event only actual ouptut
    myMockResetablePrinter.resetIfNecessary();

    //myResultsViewer.selectAndNotify(myResultsViewer.getTestsRootNode());
    //the string above doesn't update tree immediately so we should simulate update
    myConsole.getPrinter().updateOnTestSelected(myResultsViewer.getTestsRootNode());

    assertAllOutputs(myMockResetablePrinter, "preved", "","Empty test suite.\n");
  }

  private SMTestProxy startTestWithPrinter(final String testName) {
    myEventsProcessor.onTestStarted(testName, null);
    final SMTestProxy proxy =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName(testName));
    proxy.setPrintLinstener(myMockResetablePrinter);
    return proxy;
  }

  private void sendToTestProxyStdOut(final SMTestProxy proxy, final String text) {
    proxy.addLast(new Printable() {
      public void printOn(final Printer printer) {
        printer.print(text, ConsoleViewContentType.NORMAL_OUTPUT);
      }
    });
  }
}
