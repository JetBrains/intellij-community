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

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.messages.serviceMessages.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.Map;

import static com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor.getTFrameworkPrefix;

/**
 * @author Roman Chernyatchik
 *
 * This implementation also supports messages splitted in parts by early flush.
 * Implementation assumes that buffer is being flushed on line end or by timer,
 * i.e. incomming text contains no more than one line's end marker ('\r', '\n', or "\r\n")
 * (e.g. process was run with IDEA program's runner)
 */
public class OutputToGeneralTestEventsConverter implements ProcessOutputConsumer {
  private static final Logger LOG = Logger.getInstance(OutputToGeneralTestEventsConverter.class.getName());

  private static final String TEAMCITY_SERVICE_MESSAGE_PREFIX = "##teamcity[";

  private GeneralTestEventsProcessor myProcessor;
  private final MyServiceMessageVisitor myServiceMessageVisitor;
  private final String myTestFrameworkName;

  private OutputLineSplitter mySplitter;
  private boolean myPendingLineBreakFlag;

  public OutputToGeneralTestEventsConverter(@NotNull final String testFrameworkName,
                                            @NotNull final TestConsoleProperties consoleProperties) {
    myTestFrameworkName = testFrameworkName;
    myServiceMessageVisitor = new MyServiceMessageVisitor();

    mySplitter = new OutputLineSplitter(consoleProperties.isEditable()) {
      @Override
      protected void onLineAvailable(@NotNull String text, @NotNull Key outputType, boolean tcLikeFakeOutput) {
        processConsistentText(text, outputType, tcLikeFakeOutput);
      }
    };
  }

  public void setProcessor(final GeneralTestEventsProcessor processor) {
    myProcessor = processor;
  }

  public void dispose() {
    setProcessor(null);
  }

  public void process(final String text, final Key outputType) {
    mySplitter.process(text, outputType);
  }

  /**
   * Flashes the rest of stdout text buffer after output has been stopped
   */
  public void flushBufferBeforeTerminating() {
    mySplitter.flush();
    if (myPendingLineBreakFlag) {
      fireOnUncapturedLineBreak();
    }
  }

  private void fireOnUncapturedLineBreak() {
    fireOnUncapturedOutput("\n", ProcessOutputTypes.STDOUT);
  }

  private void processConsistentText(final String text, final Key outputType, boolean tcLikeFakeOutput) {
    try {
      if (!processServiceMessages(text, outputType, myServiceMessageVisitor)) {
        if (myPendingLineBreakFlag) {
          // output type for line break isn't important
          // we may use any, e.g. current one
          fireOnUncapturedLineBreak();
          myPendingLineBreakFlag = false;
        }
        // Filters \n
        String outputToProcess = text;
        if (tcLikeFakeOutput && text.endsWith("\n")) {
          // ServiceMessages protocol requires that every message
          // should start with new line, so such behaviour may led to generating
          // some number of useless \n.
          //
          // IDEA process handler flush output by size or line break
          // So:
          //  1. "a\n\nb\n" -> ["a\n", "\n", "b\n"]
          //  2. "a\n##teamcity[..]\n" -> ["a\n", "#teamcity[..]\n"]
          // We need distinguish 1) and 2) cases, in 2) first linebreak is redundant and must be ignored
          // in 2) linebreak must be considered as output
          // output will be in TestOutput message
          // Lets set myPendingLineBreakFlag if we meet "\n" and then ignore it or apply depending on
          // next output chunk
          myPendingLineBreakFlag = true;
          outputToProcess = outputToProcess.substring(0, outputToProcess.length() - 1);
        }
        //fire current output
        fireOnUncapturedOutput(outputToProcess, outputType);
      } else {
        myPendingLineBreakFlag = false;
      }
    }
    catch (ParseException e) {

      LOG.error(getTFrameworkPrefix(myTestFrameworkName) + "Error parsing text: ["+text+"]", e);
    }
  }

  protected boolean processServiceMessages(final String text,
                                          final Key outputType,
                                          final ServiceMessageVisitor visitor) throws ParseException {
    // service message parser expects line like "##teamcity[ .... ]" without whitespaces in the end.
    final ServiceMessage message = ServiceMessage.parse(text.trim());
    if (message != null) {
      message.visit(visitor);
    }
    return message != null;
  }


  private void fireOnTestStarted(final String testName, @Nullable  final String locationUrl) {
    assertNotNull(testName);

    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestStarted(testName, locationUrl);
    }
  }

  private void fireOnTestFailure(final String testName,
                                 final String localizedMessage, final String stackTrace,
                                 final boolean isTestError,
                                 @Nullable final String comparisionFailureActualText,
                                 @Nullable final String comparisionFailureExpectedText) {
    assertNotNull(testName);
    assertNotNull(localizedMessage);

     // local variable is used to prevent concurrent modification
     final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestFailure(testName, localizedMessage, stackTrace, isTestError,
                              comparisionFailureActualText,
                              comparisionFailureExpectedText);
    }
  }

  private void fireOnTestIgnored(final String testName, final String ignoreComment,
                                 @Nullable final String details) {
    assertNotNull(testName);
    assertNotNull(ignoreComment);

    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestIgnored(testName, ignoreComment, details);
    }
  }

  private void fireOnTestFinished(final String testName, final int duration) {
    assertNotNull(testName);

    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestFinished(testName, duration);
    }
  }

  private void fireOnCustomProgressTestsCategory(final String categoryName,
                                                 int testsCount) {
    assertNotNull(categoryName);

    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      final boolean disableCustomMode = StringUtil.isEmpty(categoryName);
      processor.onCustomProgressTestsCategory(disableCustomMode ? null : categoryName,
                                              disableCustomMode ? 0 : testsCount);
    }
  }

  private void fireOnCustomProgressTestStarted() {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onCustomProgressTestStarted();
    }
  }

  private void fireOnCustomProgressTestFailed() {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onCustomProgressTestFailed();
    }
  }

  private void fireOnTestFrameworkAttached() {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestsReporterAttached();
    }
  }

  private void fireOnTestOutput(final String testName, final String text, final boolean stdOut) {
    assertNotNull(testName);
    assertNotNull(text);

    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestOutput(testName, text, stdOut);
    }
  }

  private void fireOnUncapturedOutput(final String text, final Key outputType) {
    assertNotNull(text);

    if (StringUtil.isEmpty(text)) {
      return;
    }

    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onUncapturedOutput(text, outputType);
    }
  }

  private void fireOnTestsCountInSuite(final int count) {
    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestsCountInSuite(count);
    }
  }

  private void fireOnSuiteStarted(final String suiteName, @Nullable final String locationUrl) {
    assertNotNull(suiteName);

    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteStarted(suiteName, locationUrl);
    }
  }

  private void fireOnSuiteFinished(final String suiteName) {
    assertNotNull(suiteName);

    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteFinished(suiteName);
    }
  }

  protected void fireOnErrorMsg(final String localizedMessage,
                              @Nullable final String stackTrace,
                              boolean isCritical) {
    assertNotNull(localizedMessage);

    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onError(localizedMessage, stackTrace, isCritical);
    }
  }

  private void assertNotNull(final String s) {
    if (s == null) {
      LOG.error(getTFrameworkPrefix(myTestFrameworkName) + " @NotNull value is expected.");
    }
  }

  private class MyServiceMessageVisitor extends DefaultServiceMessageVisitor {
    @NonNls public static final String KEY_TESTS_COUNT = "testCount";
    @NonNls private static final String ATTR_KEY_TEST_ERROR = "error";
    @NonNls private static final String ATTR_KEY_TEST_COUNT = "count";
    @NonNls private static final String ATTR_KEY_TEST_DURATION = "duration";
    @NonNls private static final String ATTR_KEY_LOCATION_URL = "locationHint";
    @NonNls private static final String ATTR_KEY_LOCATION_URL_OLD = "location";
    @NonNls private static final String ATTR_KEY_STACKTRACE_DETAILS = "details";

    @NonNls private static final String MESSAGE = "message";
    @NonNls private static final String TEST_REPORTER_ATTACHED = "enteredTheMatrix";
    @NonNls private static final String ATTR_KEY_STATUS = "status";
    @NonNls private static final String ATTR_VALUE_STATUS_ERROR = "ERROR";
    @NonNls private static final String ATTR_VALUE_STATUS_WARNING = "WARNING";
    @NonNls private static final String ATTR_KEY_TEXT = "text";
    @NonNls private static final String ATTR_KEY_ERROR_DETAILS = "errorDetails";

    @NonNls public static final String CUSTOM_STATUS = "customProgressStatus";
    @NonNls private static final String ATTR_KEY_TEST_TYPE = "type";
    @NonNls private static final String ATTR_KEY_TESTS_CATEGORY = "testsCategory";
    @NonNls private static final String ATTR_VAL_TEST_STARTED = "testStarted";
    @NonNls private static final String ATTR_VAL_TEST_FAILED = "testFailed";

    public void visitTestSuiteStarted(@NotNull final TestSuiteStarted suiteStarted) {
      final String locationUrl = fetchTestLocation(suiteStarted);
      fireOnSuiteStarted(suiteStarted.getSuiteName(), locationUrl);
    }

    @Nullable
    private String fetchTestLocation(final TestSuiteStarted suiteStarted) {
      final Map<String, String> attrs = suiteStarted.getAttributes();
      final String location = attrs.get(ATTR_KEY_LOCATION_URL);
      if (location == null) {
        // try old API
        final String oldLocation = attrs.get(ATTR_KEY_LOCATION_URL_OLD);
        if (oldLocation != null) {
          LOG.error(getTFrameworkPrefix(myTestFrameworkName)
                    + "Test Runner API was changed for TeamCity 5.0 compatibility. Please use 'locationHint' attribute instead of 'location'.");
          return oldLocation;
        }
        return null;
      }
      return location;
    }

    public void visitTestSuiteFinished(@NotNull final TestSuiteFinished suiteFinished) {
      fireOnSuiteFinished(suiteFinished.getSuiteName());
    }

    public void visitTestStarted(@NotNull final TestStarted testStarted) {
      // TODO
      // final String locationUrl = testStarted.getLocationHint();

      final String locationUrl = testStarted.getAttributes().get(ATTR_KEY_LOCATION_URL);
      fireOnTestStarted(testStarted.getTestName(), locationUrl);
    }

    public void visitTestFinished(@NotNull final TestFinished testFinished) {
      //TODO
      //final Integer duration = testFinished.getTestDuration();
      //fireOnTestFinished(testFinished.getTestName(), duration != null ? duration.intValue() : 0);

      final String durationStr = testFinished.getAttributes().get(ATTR_KEY_TEST_DURATION);

      // Test duration in milliseconds
      int duration = 0;

      if (!StringUtil.isEmptyOrSpaces(durationStr)) {
        duration = convertToInt(durationStr);
      }
      
      fireOnTestFinished(testFinished.getTestName(), duration);
    }

    public void visitTestIgnored(@NotNull final TestIgnored testIgnored) {
      final String details = testIgnored.getAttributes().get(ATTR_KEY_STACKTRACE_DETAILS);
      fireOnTestIgnored(testIgnored.getTestName(), testIgnored.getIgnoreComment(), details);
    }

    public void visitTestStdOut(@NotNull final TestStdOut testStdOut) {
      fireOnTestOutput(testStdOut.getTestName(),testStdOut.getStdOut(), true);
    }

    public void visitTestStdErr(@NotNull final TestStdErr testStdErr) {
      fireOnTestOutput(testStdErr.getTestName(),testStdErr.getStdErr(), false);
    }

    public void visitTestFailed(@NotNull final TestFailed testFailed) {
      final boolean isTestError = testFailed.getAttributes().get(ATTR_KEY_TEST_ERROR) != null;

      fireOnTestFailure(testFailed.getTestName(), 
                        testFailed.getFailureMessage(), 
                        testFailed.getStacktrace(), 
                        isTestError,
                        testFailed.getActual(),
                        testFailed.getExpected());
    }

    public void visitPublishArtifacts(@NotNull final PublishArtifacts publishArtifacts) {
      //Do nothing
    }

    public void visitProgressMessage(@NotNull final ProgressMessage progressMessage) {
      //Do nothing
    }

    public void visitProgressStart(@NotNull final ProgressStart progressStart) {
      //Do nothing
    }

    public void visitProgressFinish(@NotNull final ProgressFinish progressFinish) {
      //Do nothing
    }

    public void visitBuildStatus(@NotNull final BuildStatus buildStatus) {
      //Do nothing
    }

    public void visitBuildNumber(@NotNull final BuildNumber buildNumber) {
      //Do nothing
    }

    public void visitBuildStatisticValue(@NotNull final BuildStatisticValue buildStatsValue) {
      //Do nothing
    }

    @Override
    public void visitMessageWithStatus(@NotNull Message msg) {
      final String name = msg.getMessageName();
        final Map<String, String> msgAttrs = msg.getAttributes();

      final String text = msgAttrs.get(ATTR_KEY_TEXT);
      if (!StringUtil.isEmpty(text)){
        // msg status
        final String status = msgAttrs.get(ATTR_KEY_STATUS);
        if (status.equals(ATTR_VALUE_STATUS_ERROR)) {
          // error msg

          final String stackTrace = msgAttrs.get(ATTR_KEY_ERROR_DETAILS);
          fireOnErrorMsg(text, stackTrace, true);
        } else if (status.equals(ATTR_VALUE_STATUS_WARNING)) {
          // warning msg

          // let's show warning via stderr
          fireOnUncapturedOutput(text, ProcessOutputTypes.STDERR);
        } else {
          // some other text

          // we cannot pass output type here but it is a service message
          // let's think that is was stdout
          fireOnUncapturedOutput(text, ProcessOutputTypes.STDOUT);
        }
      }
    }

    public void visitServiceMessage(@NotNull final ServiceMessage msg) {
      final String name = msg.getMessageName();

      if (KEY_TESTS_COUNT.equals(name)) {
        processTestCountInSuite(msg);
      } else if (CUSTOM_STATUS.equals(name)) {
        processCustomStatus(msg);
      } else if (MESSAGE.equals(name)) {
        final Map<String, String> msgAttrs = msg.getAttributes();

        final String text = msgAttrs.get(ATTR_KEY_TEXT);
        if (!StringUtil.isEmpty(text)){
          // some other text

          // we cannot pass output type here but it is a service message
          // let's think that is was stdout
          fireOnUncapturedOutput(text, ProcessOutputTypes.STDOUT);
        }
      } else if (TEST_REPORTER_ATTACHED.equals(name)) {
        fireOnTestFrameworkAttached();
      } else {
        GeneralToSMTRunnerEventsConvertor.logProblem(LOG, "Unexpected service message:" + name , myTestFrameworkName);
      }
    }

    private void processTestCountInSuite(final ServiceMessage msg) {
      final String countStr = msg.getAttributes().get(ATTR_KEY_TEST_COUNT);
      fireOnTestsCountInSuite(convertToInt(countStr));
    }

    private int convertToInt(String countStr) {
      int count = 0;
      try {
        count = Integer.parseInt(countStr);
      } catch (NumberFormatException ex) {
        LOG.error(getTFrameworkPrefix(myTestFrameworkName) + "Parse integer error.", ex);
      }
      return count;
    }

    private void processCustomStatus(final ServiceMessage msg) {
      final Map<String,String> attrs = msg.getAttributes();
      final String msgType = attrs.get(ATTR_KEY_TEST_TYPE);
      if (msgType != null) {
        if (msgType.equals(ATTR_VAL_TEST_STARTED)) {
          fireOnCustomProgressTestStarted();
        } else if (msgType.equals(ATTR_VAL_TEST_FAILED)) {
          fireOnCustomProgressTestFailed();
        }
        return;
      }
      final String testsCategory = attrs.get(ATTR_KEY_TESTS_CATEGORY);
      if (testsCategory != null) {
        final String countStr = msg.getAttributes().get(ATTR_KEY_TEST_COUNT);
        fireOnCustomProgressTestsCategory(testsCategory, convertToInt(countStr));

        //noinspection UnnecessaryReturnStatement
        return;
      }
    }
  }
}
