/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.run.testing;

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.android.run.AndroidRunningState;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 30, 2009
 * Time: 5:18:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidTestListener implements ITestRunListener {
  private final AndroidRunningState myRunningState;
  private long myTestStartingTime;
  private long myTestSuiteStartingTime;
  private String myTestClassName = null;
  private ProcessHandler myProcessHandler;

  public ProcessHandler getProcessHandler() {
    if (myProcessHandler == null) {
      myProcessHandler = myRunningState.getProcessHandler();
    }
    return myProcessHandler;
  }

  private static class TeamcityCommandBuilder {
    private final StringBuilder stringBuilder = new StringBuilder("##teamcity[");

    private TeamcityCommandBuilder(String command) {
      stringBuilder.append(command);
    }

    public void addAttribute(String name, String value) {
      stringBuilder.append(' ').append(name).append("='").append(replaceEscapeSymbols(value)).append('\'');
    }

    @Override
    public String toString() {
      return stringBuilder.toString() + ']';
    }

    private static String replaceEscapeSymbols(String text) {
      return text.replace("\\", "||").replace("'", "|'").replace("\n", "|n").replace("\r", "|r").replace("]", "|]");
    }
  }

  public AndroidTestListener(AndroidRunningState runningState) {
    myRunningState = runningState;
  }

  @Override
  public void testRunStarted(int testCount) {
    ProcessHandler handler = getProcessHandler();
    handler.notifyTextAvailable("Test running started\n", ProcessOutputTypes.STDOUT);
    TeamcityCommandBuilder builder = new TeamcityCommandBuilder("testCount");
    builder.addAttribute("count", Integer.toString(testCount));
    handler.notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  @Override
  public void testRunEnded(long elapsedTime) {
    if (myTestClassName != null) {
      testSuiteFinished();
    }
    final ProcessHandler handler = getProcessHandler();
    handler.notifyTextAvailable("Finish\n", ProcessOutputTypes.STDOUT);
    handler.destroyProcess();
  }

  public void testRunStopped(long elapsedTime) {
    ProcessHandler handler = getProcessHandler();
    handler.notifyTextAvailable("Test running stopped\n", ProcessOutputTypes.STDOUT);
    handler.destroyProcess();
  }

  public void testStarted(TestIdentifier test) {
    if (!Comparing.equal(test.getClassName(), myTestClassName)) {
      if (myTestClassName != null) {
        testSuiteFinished();
      }
      myTestClassName = test.getClassName();
      testSuiteStarted();
    }
    TeamcityCommandBuilder builder = new TeamcityCommandBuilder("testStarted");
    builder.addAttribute("name", test.getTestName());
    builder
      .addAttribute("locationHint", "android://" + myRunningState.getModule() + ':' + test.getClassName() + '.' + test.getTestName() + "()");
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
    myTestStartingTime = System.currentTimeMillis();
  }

  private void testSuiteStarted() {
    myTestSuiteStartingTime = System.currentTimeMillis();
    TeamcityCommandBuilder builder = new TeamcityCommandBuilder("testSuiteStarted");
    builder.addAttribute("name", myTestClassName);
    builder.addAttribute("locationHint", "android://" + myRunningState.getModule() + ':' + myTestClassName);
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  private void testSuiteFinished() {
    TeamcityCommandBuilder builder = new TeamcityCommandBuilder("testSuiteFinished");
    builder.addAttribute("name", myTestClassName);
    builder.addAttribute("duration", Long.toString(System.currentTimeMillis() - myTestSuiteStartingTime));
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
    myTestClassName = null;
  }

  @Override
  public void testEnded(TestIdentifier test) {
    TeamcityCommandBuilder builder = new TeamcityCommandBuilder("testFinished");
    builder.addAttribute("name", test.getTestName());
    builder.addAttribute("duration", Long.toString(System.currentTimeMillis() - myTestStartingTime));
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  public void testFailed(TestFailure status, TestIdentifier test, String stackTrace) {
    TeamcityCommandBuilder builder = new TeamcityCommandBuilder("testFailed");
    builder.addAttribute("name", test.getTestName());
    builder.addAttribute("message", "");
    builder.addAttribute("details", stackTrace);
    if (status == TestFailure.ERROR) {
      builder.addAttribute("error", "true");
    }
    getProcessHandler().notifyTextAvailable(builder.toString() + '\n', ProcessOutputTypes.STDOUT);
  }

  public void testRunFailed(String errorMessage) {
    ProcessHandler handler = getProcessHandler();
    handler.notifyTextAvailable("Test running failed: " + errorMessage + "\n", ProcessOutputTypes.STDERR);
    handler.destroyProcess();
  }
}
