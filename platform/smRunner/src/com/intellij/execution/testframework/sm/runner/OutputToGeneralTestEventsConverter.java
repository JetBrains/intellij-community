// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.process.ColoredOutputTypeRegistry;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.ServiceMessageUtil;
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

  private final MyServiceMessageVisitor myServiceMessageVisitor;
  private final String myTestFrameworkName;
  private final boolean myValidateServiceMessagesAttributes;
  private final OutputEventSplitter mySplitter;

  private volatile GeneralTestEventsProcessor myProcessor;
  private Runnable myTestingStartedHandler;
  private boolean myFirstTestingStartedEvent = true;


  public OutputToGeneralTestEventsConverter(@NotNull final String testFrameworkName, @NotNull final TestConsoleProperties consoleProperties) {
    // If console is editable, user may want to see output before new line char.
    // stdout: "enter your name:"
    // There is no new line after it, but user still wants to see this message.
    // So, if console is editable, we enable "doNotBufferTextUntilNewLine".
    this(testFrameworkName, consoleProperties.isEditable(), ( consoleProperties instanceof SMTRunnerConsoleProperties && ((SMTRunnerConsoleProperties)consoleProperties).serviceMessageHasNewLinePrefix()),
         !(consoleProperties instanceof SMTRunnerConsoleProperties) || !((SMTRunnerConsoleProperties)consoleProperties).isIdBasedTestTree());
  }

  /**
   * @param doNotBufferTextUntilNewLine opposite to {@link OutputEventSplitter} constructor
   * @param cutNewLineBeforeServiceMessage see {@link OutputEventSplitter} constructor
   * @param validateServiceMessagesAttributes whether ParseException should happen if message doesn't contain required attributes. see {@link ServiceMessagesParser#setValidateRequiredAttributes(boolean)}
   */
  public OutputToGeneralTestEventsConverter(@NotNull final String testFrameworkName,
                                            boolean doNotBufferTextUntilNewLine,
                                            boolean cutNewLineBeforeServiceMessage, 
                                            boolean validateServiceMessagesAttributes) {
    myTestFrameworkName = testFrameworkName;
    myValidateServiceMessagesAttributes = validateServiceMessagesAttributes;
    myServiceMessageVisitor = new MyServiceMessageVisitor();
    mySplitter = new OutputEventSplitter(!doNotBufferTextUntilNewLine, cutNewLineBeforeServiceMessage) {
      @Override
      public void onTextAvailable(@NotNull final String text, @NotNull final Key<?> outputType) {
        processConsistentText(text, outputType);
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
  public void flushBufferOnProcessTermination(final int exitCode) {
    mySplitter.flush();
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
   * Will be removed in 2020
   *
   * @deprecated use {@link #processConsistentText(String, Key)} instead
   */
  @Deprecated(forRemoval = true)
  protected void processConsistentText(@NotNull final String text,
                                       final Key<?> outputType,
                                       @SuppressWarnings("unused") final boolean tcLikeFakeOutput) {
    processConsistentText(text, outputType);
  }

  protected void processConsistentText(@NotNull String text, @NotNull final Key<?> outputType) {
    try {
      if (!processServiceMessages(text, outputType, myServiceMessageVisitor)) {
        //fire current output
        fireOnUncapturedOutput(text, outputType);
      }
    }
    catch (ParseException e) {

      LOG.error(getTFrameworkPrefix(myTestFrameworkName) + "Error parsing text: [" + text + "]", e);
    }
  }

  protected boolean processServiceMessages(@NotNull String text,
                                           @NotNull Key<?> outputType,
                                           @NotNull ServiceMessageVisitor visitor) throws ParseException {
    ServiceMessage message = ServiceMessageUtil.parse(text.trim(), myValidateServiceMessagesAttributes, true, myTestFrameworkName);
    if (message != null) {
      processServiceMessage(message, visitor);
    }
    return message != null;
  }

  protected void processServiceMessage(@NotNull ServiceMessage message, @NotNull ServiceMessageVisitor visitor) throws ParseException {
    message.visit(visitor);
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

  private void fireOnTestFrameworkAttached(@NotNull final TestDurationStrategy strategy) {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onTestsReporterAttached();
      processor.onDurationStrategyChanged(strategy);
    }
  }

  private void fireOnSuiteTreeNodeAdded(String testName, String locationHint, String metaInfo, String id, String parentNodeId) {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteTreeNodeAdded(testName, locationHint, metaInfo, id, parentNodeId);
    }
  }


  private void fireRootPresentationAdded(String rootName, @Nullable String comment, String rootLocation) {
    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onRootPresentationAdded(rootName, comment, rootLocation);
    }
  }

  private void fireOnSuiteTreeStarted(String suiteName, String locationHint, String metainfo, String id, String parentNodeId) {

    final GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onSuiteTreeStarted(suiteName, locationHint, metainfo, id, parentNodeId);
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
    onStartTesting();
    GeneralTestEventsProcessor processor = myProcessor;
    if (processor != null) {
      processor.onStartTesting();
    }
  }

  public void setupProcessor() {
    myTestingStartedHandler.run();
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
    @NonNls private static final String TESTING_STARTED = "testingStarted";
    @NonNls private static final String TESTING_FINISHED = "testingFinished";
    @NonNls private static final String KEY_TESTS_COUNT = "testCount";
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
    @NonNls private static final String ATTR_KEY_TEXT_ATTRIBUTES = "textAttributes";
    @NonNls private static final String ATTR_KEY_ERROR_DETAILS = "errorDetails";
    @NonNls private static final String ATTR_KEY_EXPECTED_FILE_PATH = "expectedFile";
    @NonNls private static final String ATTR_KEY_ACTUAL_FILE_PATH = "actualFile";

    @NonNls public static final String CUSTOM_STATUS = "customProgressStatus";
    @NonNls private static final String ATTR_KEY_TEST_TYPE = "type";
    @NonNls private static final String ATTR_KEY_TESTS_CATEGORY = "testsCategory";
    @NonNls private static final String ATTR_VAL_TEST_STARTED = "testStarted";
    @NonNls private static final String ATTR_VAL_TEST_FINISHED = "testFinished";
    @NonNls private static final String ATTR_VAL_TEST_FAILED = "testFailed";

    @Override
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
      final String locationUrl = attributes.get(ATTR_KEY_LOCATION_URL);
      TestStartedEvent testStartedEvent = new TestStartedEvent(testStarted, locationUrl);
      testStartedEvent.setConfig(attributes.get(ATTR_KEY_CONFIG) != null);
      fireOnTestStarted(testStartedEvent);
    }

    @Override
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

    @Override
    public void visitTestIgnored(@NotNull final TestIgnored testIgnored) {
      final String stacktrace = testIgnored.getAttributes().get(ATTR_KEY_STACKTRACE_DETAILS);
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
      String textAttributes = attributes.get(ATTR_KEY_TEXT_ATTRIBUTES);
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
      final boolean testError = attributes.get(ATTR_KEY_TEST_ERROR) != null;
      TestFailedEvent testFailedEvent = new TestFailedEvent(testFailed, testError,
                                                            attributes.get(ATTR_KEY_EXPECTED_FILE_PATH),
                                                            attributes.get(ATTR_KEY_ACTUAL_FILE_PATH));
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

      if (TESTING_STARTED.equals(name)) {
        // Since a test reporter may not emit "testingStarted"/"testingFinished" events,
        // startTesting() is already invoked before starting processing messages.
        if (!myFirstTestingStartedEvent) {
          setupProcessor();
          startTesting();
        }
        myFirstTestingStartedEvent = false;
      }
      else if (TESTING_FINISHED.equals(name)) {
        finishTesting();
      }
      else if (KEY_TESTS_COUNT.equals(name)) {
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
        fireOnTestFrameworkAttached(TestDurationStrategyKt.getDurationStrategy(msg.getAttributes().get("durationStrategy")));
      }
      else if (SUITE_TREE_STARTED.equals(name)) {
        fireOnSuiteTreeStarted(msg.getAttributes().get("name"),
                               msg.getAttributes().get(ATTR_KEY_LOCATION_URL),
                               BaseStartedNodeEvent.getMetainfo(msg),
                               TreeNodeEvent.getNodeId(msg),
                               msg.getAttributes().get("parentNodeId"));
      }
      else if (SUITE_TREE_ENDED.equals(name)) {
        fireOnSuiteTreeEnded(msg.getAttributes().get("name"));
      }
      else if (SUITE_TREE_NODE.equals(name)) {
        fireOnSuiteTreeNodeAdded(msg.getAttributes().get("name"),
                                 msg.getAttributes().get(ATTR_KEY_LOCATION_URL),
                                 BaseStartedNodeEvent.getMetainfo(msg),
                                 TreeNodeEvent.getNodeId(msg),
                                 msg.getAttributes().get("parentNodeId"));
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
        fireOnUncapturedOutput(msg.asString() + "\n", ProcessOutputTypes.STDOUT);
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
        LOG
          .error(getTFrameworkPrefix(myTestFrameworkName) + "Parse long error." + (diagnosticInfo == null ? "" : " " + diagnosticInfo), ex);
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
