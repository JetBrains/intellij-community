// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.text.StringUtil;

import java.util.Collections;
import java.util.List;

/**
 * @author Roman.Chernyatchik
 */
public class OutputToGeneralTestsEventsConverterTest extends BaseSMTRunnerTestCase {
  private ProcessOutputConsumer myOutputConsumer;
  public MockGeneralTestEventsProcessorAdapter myEnventsProcessor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final String testFrameworkName = "SMRunnerTests";
    final SMTRunnerConsoleProperties properties = new SMTRunnerConsoleProperties(createRunConfiguration(),
                                                                                 testFrameworkName,
                                                                                 DefaultRunExecutor.getRunExecutorInstance());
    myOutputConsumer = new OutputToGeneralTestEventsConverter(testFrameworkName, properties);
    myEnventsProcessor = new MockGeneralTestEventsProcessorAdapter(properties.getProject(), testFrameworkName);
    myOutputConsumer.setProcessor(myEnventsProcessor);
  }

  public void testLineBreaks_ServiceMessage() {
    doCheckOutptut("\n##teamcity[enteredTheMatrix timestamp = '2011-06-03T13:00:08.259+0400']\n", "", true);
  }

  public void testLineBreaks_NormalOutput() {
    doCheckOutptut("\na\nb\n\nc\n", "[stdout]\n" +
                                    "[stdout]a" +
                                    "[stdout]\n" +
                                    "[stdout]b" +
                                    "[stdout]\n" +
                                    "[stdout]\n" +
                                    "[stdout]c" +
                                    "[stdout]\n",
                   true);
  }

  public void testLineBreaks_OutptutAndCommands() {
    doCheckOutptut("\na\n##teamcity[enteredTheMatrix timestamp = '2011-06-03T13:00:08.259+0400']\nb\n##teamcity[enteredTheMatrix timestamp = '2011-06-03T13:00:08.259+0400']\n\nc\n",
                   "[stdout]\n" +
                   "[stdout]a" +
                   "[stdout]b" +
                   "[stdout]\n" +
                   "[stdout]c" +
                   "[stdout]\n",
                   true);
  }

  public void testLineBreaks_AutoSplitIfProcessHandlerDoestSupportIt() {
    doCheckOutptut("\na\n##teamcity[enteredTheMatrix timestamp = '2011-06-03T13:00:08.259+0400']\nb\n##teamcity[testCount count = '1' timestamp = '2011-06-03T13:00:08.259+0400']\n\nc\n",
                   "[stdout]\n" +
                   "[stdout]a" +
                   "[stdout]b" +
                   "[stdout]\n" +
                   "[stdout]c" +
                   "[stdout]\n",
                   false);
  }

  private void doCheckOutptut(String outputStr, String expected, boolean splitByLines) {
    final List<String> lines;
    if (splitByLines) {
      lines = StringUtil.split(outputStr, "\n", false);
    } else {
      lines = Collections.singletonList(outputStr);
    }
    for (String line : lines) {
      myOutputConsumer.process(line, ProcessOutputTypes.STDOUT);
    }
    myOutputConsumer.flushBufferOnProcessTermination(0);

    assertEquals(expected, myEnventsProcessor.getOutput());
  }
}
