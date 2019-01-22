// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.execution.process.ColoredOutputTypeRegistry;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
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

  private volatile GeneralTestEventsProcessor myProcessor;
  private boolean myPendingLineBreakFlag;
  private Runnable myTestingStartedHandler;
  private boolean myFirstTestingStartedEvent = true;
  private static final String ELLIPSIS = "<...>";
  private final int myCycleBufferSize = ConsoleBuffer.getCycleBufferSize();

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

  @Override
  public void setProcessor(@Nullable final GeneralTestEventsProcessor processor) {
    myProcessor = processor;
  }

  protected GeneralTestEventsProcessor getProcessor() {
    return myProcessor;
  }

  @Override
  public void dispose() {
    setProcessor(null);
  }

  @Override
  public void process(final String text, final Key outputType) {
    mySplitter.process(text, outputType);
  }

  /**
   * Flashes the rest of stdout text buffer after output has been stopped
   */
  @Override
  public void flushBufferOnProcessTermination(int exitCode) {
    mySplitter.flush();
    if (myPendingLineBreakFlag) {
      fireOnUncapturedLineBreak();
    }
  }

  private void fireOnUncapturedLineBreak() {
    fireOnUncapturedOutput("\n", ProcessOutputTypes.STDOUT);
  }

  protected void processConsistentText(String text, final Key outputType, boolean tcLikeFakeOutput) {
    if (USE_CYCLE_BUFFER && text.length() > myCycleBufferSize && myCycleBufferSize > OutputLineSplitter.SM_MESSAGE_PREFIX) {
      final StringBuilder builder = new StringBuilder(myCycleBufferSize);
      builder.append(text, 0, myCycleBufferSize - OutputLineSplitter.SM_MESSAGE_PREFIX);
      builder.append(ELLIPSIS);
      builder.append(text, text.length() - OutputLineSplitter.SM_MESSAGE_PREFIX + ELLIPSIS.length(), text.length());
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
    final ServiceMessage message;
    try {
      message = ServiceMessage.parse(text.trim());
    }
    catch (ParseException e) {
      LOG.error("Failed to parse service message", e, text);
      return false;
    }
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

  private void fireOnSuiteTreeNodeAdded(@NotNull final StartNodeEventInfo info) {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteTreeNodeAdded(info);
    }
  }


  private void fireRootPresentationAdded(String rootName, @Nullable String comment, String rootLocation) {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onRootPresentationAdded(rootName, comment, rootLocation);
    }
  }

  private void fireOnSuiteTreeStarted(@NotNull final StartNodeEventInfo info) {

    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteTreeStarted(info);
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

  public synchronized void setTestingStartedHandler(@NotNull Runnable testingStartedHandler) {
    myTestingStartedHandler = testingStartedHandler;
  }

  public void onStartTesting() {}

  public synchronized void startTesting() {
    myTestingStartedHandler.run();
    onStartTesting();
    GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onStartTesting();
    }
  }

  public synchronized void finishTesting() {
    GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      setProcessor(null);
      processor.onFinishTesting();
      Disposer.dispose(processor);
    }
  }

  private class MyServiceMessageVisitor extends DefaultServiceMessageVisitor {

    @NonNls public static final String MESSAGE = "message";

    @Override
    public void visitTestSuiteStarted(@NotNull final TestSuiteStarted suiteStarted) {
      TestSuiteStartedEvent suiteStartedEvent = new TestSuiteStartedEvent(suiteStarted);
      fireOnSuiteStarted(suiteStartedEvent);
    }


    @Override
    public void visitTestSuiteFinished(@NotNull final TestSuiteFinished suiteFinished) {
      TestSuiteFinishedEvent finishedEvent = new TestSuiteFinishedEvent(suiteFinished);
      fireOnSuiteFinished(finishedEvent);
    }

    @Override
    public void visitTestStarted(@NotNull final TestStarted testStarted) {
      // TODO
      // final String locationUrl = testStarted.getLocationHint();

      final Map<String, String> attributes = testStarted.getAttributes();
      TestStartedEvent testStartedEvent = new TestStartedEvent(testStarted);
      testStartedEvent.setConfig(attributes.get(TreeNodeEvent.ATTR_KEY_CONFIG) != null);
      fireOnTestStarted(testStartedEvent);
    }

    @Override
    public void visitTestFinished(@NotNull final TestFinished testFinished) {
      //TODO
      //final Integer duration = testFinished.getTestDuration();
      //fireOnTestFinished(testFinished.getTestName(), duration != null ? duration.intValue() : 0);

      final String durationStr = testFinished.getAttributes().get(TreeNodeEvent.ATTR_KEY_TEST_DURATION);

      // Test duration in milliseconds or null if not reported
      Long duration = null;

      if (!StringUtil.isEmptyOrSpaces(durationStr)) {
        duration = convertToLong(durationStr, testFinished);
      }

      TestFinishedEvent testFinishedEvent = new TestFinishedEvent(testFinished, duration,
                                                                  testFinished.getAttributes().get(TreeNodeEvent.ATTR_KEY_TEST_OUTPUT_FILE));
      fireOnTestFinished(testFinishedEvent);
    }

    @Override
    public void visitTestIgnored(@NotNull final TestIgnored testIgnored) {
      final String stacktrace = testIgnored.getAttributes().get(TreeNodeEvent.ATTR_KEY_STACKTRACE_DETAILS);
      fireOnTestIgnored(new TestIgnoredEvent(testIgnored, stacktrace));
    }

    @Override
    public void visitTestStdOut(@NotNull final TestStdOut testStdOut) {
      Key outputType = getOutputType(testStdOut.getAttributes(), ProcessOutputTypes.STDOUT);
      fireOnTestOutput(new TestOutputEvent(testStdOut, testStdOut.getStdOut(), outputType));
    }

    @Override
    public void visitTestStdErr(@NotNull final TestStdErr testStdErr) {
      Key outputType = getOutputType(testStdErr.getAttributes(), ProcessOutputTypes.STDERR);
      fireOnTestOutput(new TestOutputEvent(testStdErr, testStdErr.getStdErr(), outputType));
    }

    @NotNull
    public Key getOutputType(Map<String, String> attributes, Key baseOutputType) {
      String textAttributes = attributes.get(TreeNodeEvent.ATTR_KEY_TEXT_ATTRIBUTES);
      if (textAttributes == null) {
        return baseOutputType;
      }
      if (textAttributes.equals(ProcessOutputTypes.STDOUT.toString())) {
        return ProcessOutputTypes.STDOUT;
      }
      if (textAttributes.equals(ProcessOutputTypes.STDERR.toString())) {
        return ProcessOutputTypes.STDERR;
      }
      if (textAttributes.equals(ProcessOutputTypes.SYSTEM.toString())) {
        return ProcessOutputTypes.SYSTEM;
      }
      return ColoredOutputTypeRegistry.getInstance().getOutputType(textAttributes, baseOutputType);
    }

    @Override
    public void visitTestFailed(@NotNull final TestFailed testFailed) {
      final Map<String, String> attributes = testFailed.getAttributes();
      LOG.assertTrue(testFailed.getFailureMessage() != null, "No failure message for: #" + myTestFrameworkName);
      final boolean testError = attributes.get(TreeNodeEvent.ATTR_KEY_TEST_ERROR) != null;
      TestFailedEvent testFailedEvent = new TestFailedEvent(testFailed, testError,
                                                            attributes.get(TreeNodeEvent.ATTR_KEY_EXPECTED_FILE_PATH),
                                                            attributes.get(TreeNodeEvent.ATTR_KEY_ACTUAL_FILE_PATH));
      fireOnTestFailure(testFailedEvent);
    }

    @Override
    public void visitPublishArtifacts(@NotNull final PublishArtifacts publishArtifacts) {
      //Do nothing
    }

    @Override
    public void visitProgressMessage(@NotNull final ProgressMessage progressMessage) {
      //Do nothing
    }

    @Override
    public void visitProgressStart(@NotNull final ProgressStart progressStart) {
      //Do nothing
    }

    @Override
    public void visitProgressFinish(@NotNull final ProgressFinish progressFinish) {
      //Do nothing
    }

    @Override
    public void visitBuildStatus(@NotNull final BuildStatus buildStatus) {
      //Do nothing
    }

    @Override
    public void visitBuildNumber(@NotNull final BuildNumber buildNumber) {
      //Do nothing
    }

    @Override
    public void visitBuildStatisticValue(@NotNull final BuildStatisticValue buildStatsValue) {
      //Do nothing
    }

    @Override
    public void visitMessageWithStatus(@NotNull Message msg) {
      final Map<String, String> msgAttrs = msg.getAttributes();

      final String text = msgAttrs.get(TreeNodeEvent.ATTR_KEY_TEXT);
      if (!StringUtil.isEmpty(text)) {
        // msg status
        final String status = msgAttrs.get(TreeNodeEvent.ATTR_KEY_STATUS);
        if (status.equals(TreeNodeEvent.ATTR_VALUE_STATUS_ERROR)) {
          // error msg

          final String stackTrace = msgAttrs.get(TreeNodeEvent.ATTR_KEY_ERROR_DETAILS);
          fireOnErrorMsg(text, stackTrace, true);
        }
        else if (status.equals(TreeNodeEvent.ATTR_VALUE_STATUS_WARNING)) {
          // warning msg

          // let's show warning via stderr
          final String stackTrace = msgAttrs.get(TreeNodeEvent.ATTR_KEY_ERROR_DETAILS);
          fireOnErrorMsg(text, stackTrace, false);
        }
        else {
          // some other text
          fireOnUncapturedOutput(text, getOutputType(msg.getAttributes(), ProcessOutputTypes.STDOUT));
        }
      }
    }

    @Override
    public void visitServiceMessage(@NotNull final ServiceMessage msg) {
      final String name = msg.getMessageName();

      if (LOG.isDebugEnabled()) {
        LOG.debug(msg.asString());
      }

      if (TreeNodeEvent.TESTING_STARTED.equals(name)) {
        // Since a test reporter may not emit "testingStarted"/"testingFinished" events,
        // startTesting() is already invoked before starting processing messages.
        if (!myFirstTestingStartedEvent) {
          startTesting();
        }
        myFirstTestingStartedEvent = false;
      }
      else if (TreeNodeEvent.TESTING_FINISHED.equals(name)) {
        finishTesting();
      }
      else if (TreeNodeEvent.KEY_TESTS_COUNT.equals(name)) {
        processTestCountInSuite(msg);
      }
      else if (TreeNodeEvent.CUSTOM_STATUS.equals(name)) {
        processCustomStatus(msg);
      }
      else if (MESSAGE.equals(name)) {
        final Map<String, String> msgAttrs = msg.getAttributes();

        final String text = msgAttrs.get(TreeNodeEvent.ATTR_KEY_TEXT);
        if (!StringUtil.isEmpty(text)) {
          // some other text

          // we cannot pass output type here but it is a service message
          // let's think that is was stdout
          fireOnUncapturedOutput(text, ProcessOutputTypes.STDOUT);
        }
      }
      else if (TreeNodeEvent.TEST_REPORTER_ATTACHED.equals(name)) {
        fireOnTestFrameworkAttached();
        fireOnSuiteTreeStarted(StartNodeEventInfoKt.getStartNodeInfo(msg, BaseStartedNodeEvent.getName(msg)));
      }
      else if (TreeNodeEvent.SUITE_TREE_STARTED.equals(name)) {
      }
      else if (TreeNodeEvent.SUITE_TREE_ENDED.equals(name)) {
        fireOnSuiteTreeEnded(BaseStartedNodeEvent.getName(msg));
      }
      else if (TreeNodeEvent.SUITE_TREE_NODE.equals(name)) {
        fireOnSuiteTreeNodeAdded(StartNodeEventInfoKt.getStartNodeInfo(msg, BaseStartedNodeEvent.getName(msg)));
      }
      else if (TreeNodeEvent.BUILD_TREE_ENDED_NODE.equals(name)) {
        fireOnBuildTreeEnded();
      }
      else if (TreeNodeEvent.ROOT_PRESENTATION.equals(name)) {
        final Map<String, String> attributes = msg.getAttributes();
        fireRootPresentationAdded(BaseStartedNodeEvent.getName(msg), attributes.get("comment"), attributes.get("location"));
      }
      else {
        GeneralTestEventsProcessor.logProblem(LOG, "Unexpected service message:" + name, myTestFrameworkName);
      }
    }



    private void processTestCountInSuite(final ServiceMessage msg) {
      final String countStr = msg.getAttributes().get(TreeNodeEvent.ATTR_KEY_TEST_COUNT);
      fireOnTestsCountInSuite(convertToInt(countStr, msg));
    }

    private int convertToInt(String countStr, final ServiceMessage msg) {
      int count = 0;
      try {
        count = Integer.parseInt(countStr);
      }
      catch (NumberFormatException ex) {
        final String diagnosticInfo = msg.getAttributes().get(TreeNodeEvent.ATTR_KEY_DIAGNOSTIC);
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
        final String diagnosticInfo = msg.getAttributes().get(TreeNodeEvent.ATTR_KEY_DIAGNOSTIC);
        LOG
          .error(getTFrameworkPrefix(myTestFrameworkName) + "Parse long error." + (diagnosticInfo == null ? "" : " " + diagnosticInfo), ex);
      }
      return count;
    }

    private void processCustomStatus(final ServiceMessage msg) {
      final Map<String, String> attrs = msg.getAttributes();
      final String msgType = attrs.get(TreeNodeEvent.ATTR_KEY_TEST_TYPE);
      if (msgType != null) {
        if (msgType.equals(TreeNodeEvent.ATTR_VAL_TEST_STARTED)) {
          fireOnCustomProgressTestStarted();
        }
        else if (msgType.equals(TreeNodeEvent.ATTR_VAL_TEST_FINISHED)) {
          fireOnCustomProgressTestFinished();
        }
        else if (msgType.equals(TreeNodeEvent.ATTR_VAL_TEST_FAILED)) {
          fireOnCustomProgressTestFailed();
        }
        return;
      }
      final String testsCategory = attrs.get(TreeNodeEvent.ATTR_KEY_TESTS_CATEGORY);
      if (testsCategory != null) {
        final String countStr = msg.getAttributes().get(TreeNodeEvent.ATTR_KEY_TEST_COUNT);
        fireOnCustomProgressTestsCategory(testsCategory, convertToInt(countStr, msg));

        //noinspection UnnecessaryReturnStatement
        return;
      }
    }
  }
}
