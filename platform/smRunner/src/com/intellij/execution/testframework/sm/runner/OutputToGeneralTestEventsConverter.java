/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.events.*;
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
 * This implementation also supports messages split in parts by early flush.
 * Implementation assumes that buffer is being flushed on line end or by timer,
 * i.e. incoming text contains no more than one line's end marker ('\r', '\n', or "\r\n")
 * (e.g. process was run with IDEA program's runner)
 *
 * @author Roman Chernyatchik
 */
public class OutputToGeneralTestEventsConverter implements ProcessOutputConsumer {
  private static final Logger LOG = Logger.getInstance(OutputToGeneralTestEventsConverter.class.getName());
  private static final boolean USE_CYCLE_BUFFER = ConsoleBuffer.useCycleBuffer();

  private final MyServiceMessageVisitor myServiceMessageVisitor;
  private final String myTestFrameworkName;
  private final OutputLineSplitter mySplitter;

  private GeneralTestEventsProcessor myProcessor;
  private boolean myPendingLineBreakFlag;

  public OutputToGeneralTestEventsConverter(@NotNull String testFrameworkName, @NotNull TestConsoleProperties consoleProperties) {
    this(testFrameworkName, consoleProperties.isEditable());
  }

  public OutputToGeneralTestEventsConverter(@NotNull String testFrameworkName, boolean stdinEnabled) {
    myTestFrameworkName = testFrameworkName;
    myServiceMessageVisitor = new MyServiceMessageVisitor();
    mySplitter = new OutputLineSplitter(stdinEnabled) {
      @Override
      protected void onLineAvailable(@NotNull String text, @NotNull Key outputType, boolean tcLikeFakeOutput) {
        processConsistentText(text, outputType, tcLikeFakeOutput);
      }
    };
  }

  public void setProcessor(@Nullable final GeneralTestEventsProcessor processor) {
    myProcessor = processor;
  }

  protected GeneralTestEventsProcessor getProcessor() {
    return myProcessor;
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

  protected void processConsistentText(String text, final Key outputType, boolean tcLikeFakeOutput) {
    final int cycleBufferSize = ConsoleBuffer.getCycleBufferSize();
    if (USE_CYCLE_BUFFER && text.length() > cycleBufferSize) {
      final StringBuilder builder = new StringBuilder(cycleBufferSize);
      builder.append(text, 0, cycleBufferSize - 105);
      builder.append("<...>");
      builder.append(text, text.length() - 100, text.length());
      text = builder.toString();
    }

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
      }
      else {
        myPendingLineBreakFlag = false;
      }
    }
    catch (ParseException e) {

      LOG.error(getTFrameworkPrefix(myTestFrameworkName) + "Error parsing text: [" + text + "]", e);
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


  private void fireOnTestStarted(@NotNull TestStartedEvent testStartedEvent) {
    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestStarted(testStartedEvent);
    }
  }

  private void fireOnTestFailure(@NotNull TestFailedEvent testFailedEvent) {
    assertNotNull(testFailedEvent.getLocalizedFailureMessage());

    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestFailure(testFailedEvent);
    }
  }

  private void fireOnTestIgnored(@NotNull TestIgnoredEvent testIgnoredEvent) {

    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestIgnored(testIgnoredEvent);
    }
  }

  private void fireOnTestFinished(@NotNull TestFinishedEvent testFinishedEvent) {
    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestFinished(testFinishedEvent);
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

  private void fireOnCustomProgressTestFinished() {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onCustomProgressTestFinished();
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

  private void fireOnSuiteTreeNodeAdded(String testName, String locationHint, String id, String parentNodeId) {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteTreeNodeAdded(testName, locationHint, id, parentNodeId);
    }
  }


  private void fireRootPresentationAdded(String rootName, @Nullable String comment, String rootLocation) {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onRootPresentationAdded(rootName, comment, rootLocation);
    }
  }

  private void fireOnSuiteTreeStarted(String suiteName, String locationHint, String id, String parentNodeId) {

    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteTreeStarted(suiteName, locationHint, id, parentNodeId);
    }
  }

  private void fireOnSuiteTreeEnded(String suiteName) {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteTreeEnded(suiteName);
    }
  }
  
  private void fireOnBuildTreeEnded() {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onBuildTreeEnded();
    }
  }

  private void fireOnTestOutput(@NotNull TestOutputEvent testOutputEvent) {
    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestOutput(testOutputEvent);
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

  private void fireOnSuiteStarted(@NotNull TestSuiteStartedEvent suiteStartedEvent) {
    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteStarted(suiteStartedEvent);
    }
  }

  private void fireOnSuiteFinished(@NotNull TestSuiteFinishedEvent suiteFinishedEvent) {
    // local variable is used to prevent concurrent modification
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteFinished(suiteFinishedEvent);
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

  public void onStartTesting() {}

  private class MyServiceMessageVisitor extends DefaultServiceMessageVisitor {
    @NonNls public static final String KEY_TESTS_COUNT = "testCount";
    @NonNls private static final String ATTR_KEY_TEST_ERROR = "error";
    @NonNls private static final String ATTR_KEY_TEST_COUNT = "count";
    @NonNls private static final String ATTR_KEY_TEST_DURATION = "duration";
    @NonNls private static final String ATTR_KEY_TEST_OUTPUT_FILE = "outputFile";
    @NonNls private static final String ATTR_KEY_LOCATION_URL = "locationHint";
    @NonNls private static final String ATTR_KEY_LOCATION_URL_OLD = "location";
    @NonNls private static final String ATTR_KEY_STACKTRACE_DETAILS = "details";
    @NonNls private static final String ATTR_KEY_DIAGNOSTIC = "diagnosticInfo";
    @NonNls private static final String ATTR_KEY_CONFIG = "config";

    @NonNls private static final String MESSAGE = "message";
    @NonNls private static final String TEST_REPORTER_ATTACHED = "enteredTheMatrix";
    @NonNls private static final String SUITE_TREE_STARTED = "suiteTreeStarted";
    @NonNls private static final String SUITE_TREE_ENDED = "suiteTreeEnded";
    @NonNls private static final String SUITE_TREE_NODE = "suiteTreeNode";
    @NonNls private static final String BUILD_TREE_ENDED_NODE = "treeEnded";
    @NonNls private static final String ROOT_PRESENTATION = "rootName";

    @NonNls private static final String ATTR_KEY_STATUS = "status";
    @NonNls private static final String ATTR_VALUE_STATUS_ERROR = "ERROR";
    @NonNls private static final String ATTR_VALUE_STATUS_WARNING = "WARNING";
    @NonNls private static final String ATTR_KEY_TEXT = "text";
    @NonNls private static final String ATTR_KEY_ERROR_DETAILS = "errorDetails";
    @NonNls private static final String ATTR_KEY_EXPECTED_FILE_PATH = "expectedFile";
    @NonNls private static final String ATTR_KEY_ACTUAL_FILE_PATH = "actualFile";

    @NonNls public static final String CUSTOM_STATUS = "customProgressStatus";
    @NonNls private static final String ATTR_KEY_TEST_TYPE = "type";
    @NonNls private static final String ATTR_KEY_TESTS_CATEGORY = "testsCategory";
    @NonNls private static final String ATTR_VAL_TEST_STARTED = "testStarted";
    @NonNls private static final String ATTR_VAL_TEST_FINISHED = "testFinished";
    @NonNls private static final String ATTR_VAL_TEST_FAILED = "testFailed";

    public void visitTestSuiteStarted(@NotNull final TestSuiteStarted suiteStarted) {
      final String locationUrl = fetchTestLocation(suiteStarted);
      TestSuiteStartedEvent suiteStartedEvent = new TestSuiteStartedEvent(suiteStarted, locationUrl);
      fireOnSuiteStarted(suiteStartedEvent);
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
                    +
                    "Test Runner API was changed for TeamCity 5.0 compatibility. Please use 'locationHint' attribute instead of 'location'.");
          return oldLocation;
        }
        return null;
      }
      return location;
    }

    public void visitTestSuiteFinished(@NotNull final TestSuiteFinished suiteFinished) {
      TestSuiteFinishedEvent finishedEvent = new TestSuiteFinishedEvent(suiteFinished);
      fireOnSuiteFinished(finishedEvent);
    }

    public void visitTestStarted(@NotNull final TestStarted testStarted) {
      // TODO
      // final String locationUrl = testStarted.getLocationHint();

      final Map<String, String> attributes = testStarted.getAttributes();
      final String locationUrl = attributes.get(ATTR_KEY_LOCATION_URL);
      TestStartedEvent testStartedEvent = new TestStartedEvent(testStarted, locationUrl);
      testStartedEvent.setConfig(attributes.get(ATTR_KEY_CONFIG) != null);
      fireOnTestStarted(testStartedEvent);
    }

    public void visitTestFinished(@NotNull final TestFinished testFinished) {
      //TODO
      //final Integer duration = testFinished.getTestDuration();
      //fireOnTestFinished(testFinished.getTestName(), duration != null ? duration.intValue() : 0);

      final String durationStr = testFinished.getAttributes().get(ATTR_KEY_TEST_DURATION);

      // Test duration in milliseconds or null if not reported
      Long duration = null;

      if (!StringUtil.isEmptyOrSpaces(durationStr)) {
        duration = convertToLong(durationStr, testFinished);
      }

      TestFinishedEvent testFinishedEvent = new TestFinishedEvent(testFinished, duration, 
                                                                  testFinished.getAttributes().get(ATTR_KEY_TEST_OUTPUT_FILE));
      fireOnTestFinished(testFinishedEvent);
    }

    public void visitTestIgnored(@NotNull final TestIgnored testIgnored) {
      final String stacktrace = testIgnored.getAttributes().get(ATTR_KEY_STACKTRACE_DETAILS);
      fireOnTestIgnored(new TestIgnoredEvent(testIgnored, stacktrace));
    }

    public void visitTestStdOut(@NotNull final TestStdOut testStdOut) {
      fireOnTestOutput(new TestOutputEvent(testStdOut, testStdOut.getStdOut(), true));
    }

    public void visitTestStdErr(@NotNull final TestStdErr testStdErr) {
      fireOnTestOutput(new TestOutputEvent(testStdErr, testStdErr.getStdErr(), false));
    }

    public void visitTestFailed(@NotNull final TestFailed testFailed) {
      final Map<String, String> attributes = testFailed.getAttributes();
      LOG.assertTrue(testFailed.getFailureMessage() != null, "No failure message for: " + myTestFrameworkName);
      final boolean testError = attributes.get(ATTR_KEY_TEST_ERROR) != null;
      TestFailedEvent testFailedEvent = new TestFailedEvent(testFailed, testError, 
                                                            attributes.get(ATTR_KEY_EXPECTED_FILE_PATH),
                                                            attributes.get(ATTR_KEY_ACTUAL_FILE_PATH));
      fireOnTestFailure(testFailedEvent);
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
      final Map<String, String> msgAttrs = msg.getAttributes();

      final String text = msgAttrs.get(ATTR_KEY_TEXT);
      if (!StringUtil.isEmpty(text)) {
        // msg status
        final String status = msgAttrs.get(ATTR_KEY_STATUS);
        if (status.equals(ATTR_VALUE_STATUS_ERROR)) {
          // error msg

          final String stackTrace = msgAttrs.get(ATTR_KEY_ERROR_DETAILS);
          fireOnErrorMsg(text, stackTrace, true);
        }
        else if (status.equals(ATTR_VALUE_STATUS_WARNING)) {
          // warning msg

          // let's show warning via stderr
          final String stackTrace = msgAttrs.get(ATTR_KEY_ERROR_DETAILS);
          fireOnErrorMsg(text, stackTrace, false);
        }
        else {
          // some other text

          // we cannot pass output type here but it is a service message
          // let's think that is was stdout
          fireOnUncapturedOutput(text, ProcessOutputTypes.STDOUT);
        }
      }
    }

    public void visitServiceMessage(@NotNull final ServiceMessage msg) {
      final String name = msg.getMessageName();

      if (LOG.isDebugEnabled()) {
        LOG.debug(msg.asString());
      }

      if (KEY_TESTS_COUNT.equals(name)) {
        processTestCountInSuite(msg);
      }
      else if (CUSTOM_STATUS.equals(name)) {
        processCustomStatus(msg);
      }
      else if (MESSAGE.equals(name)) {
        final Map<String, String> msgAttrs = msg.getAttributes();

        final String text = msgAttrs.get(ATTR_KEY_TEXT);
        if (!StringUtil.isEmpty(text)) {
          // some other text

          // we cannot pass output type here but it is a service message
          // let's think that is was stdout
          fireOnUncapturedOutput(text, ProcessOutputTypes.STDOUT);
        }
      }
      else if (TEST_REPORTER_ATTACHED.equals(name)) {
        fireOnTestFrameworkAttached();
      }
      else if (SUITE_TREE_STARTED.equals(name)) {
        fireOnSuiteTreeStarted(msg.getAttributes().get("name"), msg.getAttributes().get(ATTR_KEY_LOCATION_URL), TreeNodeEvent.getNodeId(msg), msg.getAttributes().get("parentNodeId"));
      }
      else if (SUITE_TREE_ENDED.equals(name)) {
        fireOnSuiteTreeEnded(msg.getAttributes().get("name"));
      }
      else if (SUITE_TREE_NODE.equals(name)) {
        fireOnSuiteTreeNodeAdded(msg.getAttributes().get("name"), msg.getAttributes().get(ATTR_KEY_LOCATION_URL), TreeNodeEvent.getNodeId(msg), msg.getAttributes().get("parentNodeId"));
      }
      else if (BUILD_TREE_ENDED_NODE.equals(name)) {
        fireOnBuildTreeEnded();
      }
      else if (ROOT_PRESENTATION.equals(name)) {
        final Map<String, String> attributes = msg.getAttributes();
        fireRootPresentationAdded(attributes.get("name"), attributes.get("comment"), attributes.get("location"));
      }
      else {
        GeneralTestEventsProcessor.logProblem(LOG, "Unexpected service message:" + name, myTestFrameworkName);
      }
    }

    private void processTestCountInSuite(final ServiceMessage msg) {
      final String countStr = msg.getAttributes().get(ATTR_KEY_TEST_COUNT);
      fireOnTestsCountInSuite(convertToInt(countStr, msg));
    }

    private int convertToInt(String countStr, final ServiceMessage msg) {
      int count = 0;
      try {
        count = Integer.parseInt(countStr);
      }
      catch (NumberFormatException ex) {
        final String diagnosticInfo = msg.getAttributes().get(ATTR_KEY_DIAGNOSTIC);
        LOG.error(getTFrameworkPrefix(myTestFrameworkName) + "Parse integer error." + (diagnosticInfo == null ? "" : " " + diagnosticInfo),
                  ex);
      }
      return count;
    }

    private long convertToLong(final String countStr, @NotNull final ServiceMessage msg) {
      long count = 0;
      try {
        count = Long.parseLong(countStr);
      }
      catch (NumberFormatException ex) {
        final String diagnosticInfo = msg.getAttributes().get(ATTR_KEY_DIAGNOSTIC);
        LOG.error(getTFrameworkPrefix(myTestFrameworkName) + "Parse long error." + (diagnosticInfo == null ? "" : " " + diagnosticInfo), ex);
      }
      return count;
    }

    private void processCustomStatus(final ServiceMessage msg) {
      final Map<String, String> attrs = msg.getAttributes();
      final String msgType = attrs.get(ATTR_KEY_TEST_TYPE);
      if (msgType != null) {
        if (msgType.equals(ATTR_VAL_TEST_STARTED)) {
          fireOnCustomProgressTestStarted();
        }
        else if (msgType.equals(ATTR_VAL_TEST_FINISHED)) {
          fireOnCustomProgressTestFinished();
        }
        else if (msgType.equals(ATTR_VAL_TEST_FAILED)) {
          fireOnCustomProgressTestFailed();
        }
        return;
      }
      final String testsCategory = attrs.get(ATTR_KEY_TESTS_CATEGORY);
      if (testsCategory != null) {
        final String countStr = msg.getAttributes().get(ATTR_KEY_TEST_COUNT);
        fireOnCustomProgressTestsCategory(testsCategory, convertToInt(countStr, msg));

        //noinspection UnnecessaryReturnStatement
        return;
      }
    }
  }
}
