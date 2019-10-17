package org.jetbrains.plugins.cucumber.java.run;

import com.intellij.junit4.ExpectedPatterns;
import com.intellij.rt.execution.junit.ComparisonFailureData;
import cucumber.api.event.*;
import cucumber.api.formatter.Formatter;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatterUtil.*;

@SuppressWarnings("unused")
public class CucumberJvm2SMFormatter implements Formatter {
  private static final String EXAMPLES_CAPTION = "Examples:";
  private static final String SCENARIO_OUTLINE_CAPTION = "Scenario: Line: ";
  private final Map<String, String> pathToDescription = new HashMap<String, String>();
  private String currentFilePath;
  private int currentScenarioOutlineLine;
  private String currentScenarioOutlineName;
  private final PrintStream myOut;
  private final String myCurrentTimeValue;

  public CucumberJvm2SMFormatter() {
    //noinspection UseOfSystemOutOrSystemErr
    this(System.out, null);
  }

  public CucumberJvm2SMFormatter(PrintStream out, String currentTimeValue) {
    myOut = out;
    myCurrentTimeValue = currentTimeValue;
    outCommand(TEMPLATE_ENTER_THE_MATRIX, getCurrentTime());
    outCommand(TEMPLATE_SCENARIO_COUNTING_STARTED, "0", getCurrentTime());
  }

  private final EventHandler<TestCaseStarted> testCaseStartedHandler = new EventHandler<TestCaseStarted>() {
    @Override
    public void receive(TestCaseStarted event) {
      CucumberJvm2SMFormatter.this.handleTestCaseStarted(new CucumberJvm2Adapter.IdeaTestCaseEvent(event));
    }
  };

  private final EventHandler<TestCaseFinished> testCaseFinishedHandler = new EventHandler<TestCaseFinished>() {
    @Override
    public void receive(TestCaseFinished event) {
      handleTestCaseFinished(new CucumberJvm2Adapter.IdeaTestCaseEvent(event));
    }
  };

  private final EventHandler<TestRunFinished> testRunFinishedHandler = new EventHandler<TestRunFinished>() {
    @Override
    public void receive(TestRunFinished event) {
      CucumberJvm2SMFormatter.this.handleTestRunFinished();
    }
  };

  private final EventHandler<WriteEvent> writeEventHandler = new EventHandler<WriteEvent>() {
    @Override
    public void receive(WriteEvent event) {
      CucumberJvm2SMFormatter.this.handleWriteEvent(new CucumberJvmAdapter.IdeaWriteEvent(event.text));
    }
  };

  private final EventHandler<TestStepStarted> testStepStartedHandler = new EventHandler<TestStepStarted>() {
    @Override
    public void receive(TestStepStarted event) {
      handleTestStepStarted(new CucumberJvm2Adapter.IdeaTestStepEvent(event));
    }
  };

  private final EventHandler<TestStepFinished> testStepFinishedHandler = new EventHandler<TestStepFinished>() {
    @Override
    public void receive(TestStepFinished event) {
      handleTestStepFinished(new CucumberJvm2Adapter.IdeaTestStepFinishedEvent(event));
    }
  };

  private final EventHandler<TestSourceRead> testSourceReadHandler = new EventHandler<TestSourceRead>() {
    @Override
    public void receive(TestSourceRead event) {
      CucumberJvm2SMFormatter.this.handleTestSourceRead(new CucumberJvm2Adapter.IdeaTestSourceReadEvent(event));
    }
  };

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    publisher.registerHandlerFor(TestCaseStarted.class, this.testCaseStartedHandler);
    publisher.registerHandlerFor(TestCaseFinished.class, this.testCaseFinishedHandler);

    publisher.registerHandlerFor(TestStepStarted.class, this.testStepStartedHandler);
    publisher.registerHandlerFor(TestStepFinished.class, this.testStepFinishedHandler);
    publisher.registerHandlerFor(TestSourceRead.class, this.testSourceReadHandler);

    publisher.registerHandlerFor(TestRunFinished.class, this.testRunFinishedHandler);
    publisher.registerHandlerFor(WriteEvent.class, this.writeEventHandler);
  }

  private void handleTestCaseStarted(CucumberJvmAdapter.IdeaTestCaseEvent event) {
    if (currentFilePath == null) {
      outCommand(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), getEventUri(event), getFeatureFileDescription(getEventUri(event)));
    }
    else if (!getEventUri(event).equals(currentFilePath)) {
      closeCurrentScenarioOutline();
      outCommand(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), getFeatureFileDescription(currentFilePath));
      outCommand(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), getEventUri(event), getFeatureFileDescription(getEventUri(event)));
    }

    outCommand(TEMPLATE_SCENARIO_STARTED, getCurrentTime());

    if (event.getTestCase().isScenarioOutline()) {
      int mainScenarioLine = event.getTestCase().getScenarioOutlineLine();
      if (currentScenarioOutlineLine != mainScenarioLine || currentFilePath == null ||
          !currentFilePath.equals(getEventUri(event))) {
        closeCurrentScenarioOutline();
        currentScenarioOutlineLine = mainScenarioLine;
        currentScenarioOutlineName = event.getTestCase().getScenarioName();
        outCommand(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), getEventUri(event) + ":" + currentScenarioOutlineLine,
                   currentScenarioOutlineName);
        outCommand(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), "", EXAMPLES_CAPTION);
      }
    } else {
      closeCurrentScenarioOutline();
    }
    currentFilePath = getEventUri(event);

    outCommand(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), getEventUri(event) + ":" + event.getTestCase().getLine(),
               event.getTestCase().getScenarioName());
  }

  private void handleTestCaseFinished(CucumberJvmAdapter.IdeaTestCaseEvent event) {
    outCommand(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), event.getTestCase().getScenarioName());
    outCommand(TEMPLATE_SCENARIO_FINISHED, getCurrentTime());
  }

  private void handleTestRunFinished() {
    closeCurrentScenarioOutline();
    outCommand(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), getFeatureFileDescription(currentFilePath));
  }

  private void handleWriteEvent(CucumberJvmAdapter.IdeaWriteEvent event) {
    myOut.println(event.getText());
  }

  private void handleTestStepStarted(CucumberJvmAdapter.IdeaTestStepEvent event) {
    outCommand(TEMPLATE_TEST_STARTED, getCurrentTime(), event.getTestStep().getLocation(), event.getTestStep().getStepName());
  }

  private void handleTestStepFinished(CucumberJvmAdapter.IdeaTestStepFinishedEvent event) {
    if (event.getResult() == CucumberJvmAdapter.IdeaTestStepFinishedEvent.Status.PASSED) {
      // write nothing
    }
    else if (event.getResult() == CucumberJvmAdapter.IdeaTestStepFinishedEvent.Status.SKIPPED ||
             event.getResult() == CucumberJvmAdapter.IdeaTestStepFinishedEvent.Status.PENDING) {
      outCommand(TEMPLATE_TEST_PENDING, event.getTestStep().getStepName(), getCurrentTime());
    }
    else {
      String[] messageAndDetails = getMessageAndDetails(event.getErrorMessage());

      ComparisonFailureData comparisonFailureData = ExpectedPatterns.createExceptionNotification(messageAndDetails[0]);
      if (comparisonFailureData != null) {
        outCommand(TEMPLATE_COMPARISON_TEST_FAILED, getCurrentTime(), messageAndDetails[1], messageAndDetails[0],
                   comparisonFailureData.getExpected(), comparisonFailureData.getActual(), event.getTestStep().getStepName(), "");
      }
      else {
        outCommand(TEMPLATE_TEST_FAILED, getCurrentTime(), "", event.getErrorMessage(), event.getTestStep().getStepName(), "");
      }
    }
    outCommand(TEMPLATE_TEST_FINISHED, getCurrentTime(), String.valueOf(event.getDuration()), event.getTestStep().getStepName());
  }

  private String getFeatureFileDescription(String uri) {
    if (pathToDescription.containsKey(uri)) {
      return pathToDescription.get(uri);
    }
    return uri;
  }

  private void handleTestSourceRead(CucumberJvm2Adapter.IdeaTestSourceReadEvent event) {
    closeCurrentScenarioOutline();
    pathToDescription.put(event.getUri(), getFeatureName(event.getSource()));
  }

  private void closeCurrentScenarioOutline() {
    if (currentScenarioOutlineLine > 0) {
      outCommand(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), EXAMPLES_CAPTION);
      outCommand(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), currentScenarioOutlineName);
      currentScenarioOutlineLine = 0;
      currentScenarioOutlineName = null;
    }
  }

  private static String[] getMessageAndDetails(String errorReport) {
    if (errorReport == null) {
      errorReport = "";
    }
    String[] messageAndDetails = errorReport.split("\n", 2);

    String message = null;
    if (messageAndDetails.length > 0) {
      message = messageAndDetails[0];
    }
    if (message == null) {
      message = "";
    }

    String details = null;
    if (messageAndDetails.length > 1) {
      details = messageAndDetails[1];
    }
    if (details == null) {
      details = "";
    }

    return new String[] {message, details};
  }

  private void outCommand(String command, String... parameters) {
    myOut.println(escapeCommand(command, parameters));
  }

  private static String getScenarioName(CucumberJvmAdapter.IdeaTestCase testCase) {
    if (testCase.isScenarioOutline()) {
      return SCENARIO_OUTLINE_CAPTION + testCase.getLine();
    }
    return testCase.getScenarioName();
  }

  protected String getEventUri(CucumberJvmAdapter.IdeaTestCaseEvent event) {
    return event.getUri();
  }

  private String getCurrentTime() {
    if (myCurrentTimeValue != null) {
      return myCurrentTimeValue;
    }
    return CucumberJvmSMFormatterUtil.getCurrentTime();
  }
}
