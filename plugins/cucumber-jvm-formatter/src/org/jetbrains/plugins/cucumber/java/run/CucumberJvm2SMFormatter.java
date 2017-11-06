package org.jetbrains.plugins.cucumber.java.run;

import cucumber.api.TestCase;
import cucumber.api.TestStep;
import cucumber.api.event.*;
import cucumber.api.formatter.Formatter;
import gherkin.events.PickleEvent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static cucumber.api.Result.Type.*;
import static org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatterUtil.*;
import static org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatterUtil.escape;

@SuppressWarnings("unused")
public class CucumberJvm2SMFormatter implements Formatter {
  private static final String EXAMPLES_CAPTION = "Examples:";
  private static final String SCENARIO_OUTLINE_CAPTION = "Scenario: Line: ";
  private Map<String, String> pathToDescription = new HashMap<String, String>();
  private String currentFilePath;
  private int currentScenarioOutlineLine;
  private String currentScenarioOutlineName;

  public CucumberJvm2SMFormatter() {
    outCommand(String.format(TEMPLATE_ENTER_THE_MATRIX, getCurrentTime()));
    outCommand(String.format(TEMPLATE_SCENARIO_COUNTING_STARTED, 0, getCurrentTime()));
  }

  private EventHandler<TestCaseStarted> testCaseStartedHandler = new EventHandler<TestCaseStarted>() {
    public void receive(TestCaseStarted event) {
      CucumberJvm2SMFormatter.this.handleTestCaseStarted(event);
    }
  };

  private EventHandler<TestCaseFinished> testCaseFinishedHandler = new EventHandler<TestCaseFinished>() {
    public void receive(TestCaseFinished event) {
      handleTestCaseFinished(event);
    }
  };

  private EventHandler<TestRunFinished> testRunFinishedHandler = new EventHandler<TestRunFinished>() {
    public void receive(TestRunFinished event) {
      CucumberJvm2SMFormatter.this.handleTestRunFinished(event);
    }
  };

  private EventHandler<TestStepStarted> testStepStartedHandler = new EventHandler<TestStepStarted>() {
    public void receive(TestStepStarted event) {
      handleTestStepStarted(event);
    }
  };

  private EventHandler<TestStepFinished> testStepFinishedHandler = new EventHandler<TestStepFinished>() {
    public void receive(TestStepFinished event) {
      handleTestStepFinished(event);
    }
  };

  private EventHandler<TestSourceRead> testSourceReadHandler = new EventHandler<TestSourceRead>() {
    public void receive(TestSourceRead event) {
      CucumberJvm2SMFormatter.this.handleTestSourceRead(event);
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
  }

  private void handleTestCaseStarted(TestCaseStarted event) {
    if (currentFilePath == null) {
      outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), event.testCase.getUri(),
                               getFeatureFileDescription(event.testCase.getUri())));
    }
    else if (!event.testCase.getUri().equals(currentFilePath)) {
      closeCurrentScenarioOutline();
      outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(),
                               getFeatureFileDescription(currentFilePath)));
      outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), event.testCase.getUri(),
                               getFeatureFileDescription(event.testCase.getUri())));
    }

    if (isScenarioOutline(event.testCase)) {
      int mainScenarioLine = getScenarioOutlineLine(event.testCase);
      if (currentScenarioOutlineLine != mainScenarioLine || currentFilePath == null ||
          !currentFilePath.equals(event.testCase.getUri())) {
        closeCurrentScenarioOutline();
        currentScenarioOutlineLine = mainScenarioLine;
        currentScenarioOutlineName = event.testCase.getName();
        outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(),
                                 event.testCase.getUri() + ":" + currentScenarioOutlineLine, currentScenarioOutlineName));
        outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), "", EXAMPLES_CAPTION));
      }
    } else {
      closeCurrentScenarioOutline();
    }
    currentFilePath = event.testCase.getUri();

    outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(),
                             event.testCase.getUri() + ":" + event.testCase.getLine(), getScenarioName(event.testCase)));
  }

  private static void handleTestCaseFinished(TestCaseFinished event) {
    outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), getScenarioName(event.testCase)));
  }

  private void handleTestRunFinished(TestRunFinished event) {
    closeCurrentScenarioOutline();
    outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(),
                             getFeatureFileDescription(currentFilePath)));
  }
  private static void handleTestStepStarted(TestStepStarted event) {
    if (event.testStep.isHook()) {
      return;
    }

    outCommand(String.format(TEMPLATE_TEST_STARTED, getCurrentTime(), getStepLocation(event.testStep),
                             getStepName(event.testStep)));
  }

  private static void handleTestStepFinished(TestStepFinished event) {
    if (event.testStep.isHook()) {
      return;
    }
    if (event.result.getStatus() == PASSED) {
      // write nothing
    } else if (event.result.getStatus() == SKIPPED || event.result.getStatus() == PENDING) {
      outCommand(String.format(TEMPLATE_TEST_PENDING, getStepName(event.testStep), getCurrentTime()));
    } else {
      outCommand(String.format(TEMPLATE_TEST_FAILED, getCurrentTime(), "",
                               escape(event.result.getErrorMessage()), getStepName(event.testStep), ""));
    }
    Long duration = event.result.getDuration() != null ? event.result.getDuration() / 1000000: 0;
    outCommand(String.format(TEMPLATE_TEST_FINISHED, getCurrentTime(), duration, getStepName(event.testStep)));
  }

  private String getFeatureFileDescription(String uri) {
    if (pathToDescription.containsKey(uri)) {
      return pathToDescription.get(uri);
    }
    return uri;
  }

  private void handleTestSourceRead(TestSourceRead event) {
    closeCurrentScenarioOutline();

    String[] lines = event.source.split("\n");
    if (lines.length > 0) {
      pathToDescription.put(event.uri, lines[0]);
    } else {
      pathToDescription.put(event.uri, event.source);
    }
  }

  private void closeCurrentScenarioOutline() {
    if (currentScenarioOutlineLine > 0) {
      outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), EXAMPLES_CAPTION));
      outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), currentScenarioOutlineName));
      currentScenarioOutlineLine = 0;
      currentScenarioOutlineName = null;
    }
  }

  private static String getStepLocation(TestStep step) {
    return step.getStepLocation() + ":" + step.getStepLine();
  }

  private static String getStepName(TestStep step) {
    return escape(step.getStepText());
  }

  private static void outCommand(String s) {
    //noinspection UseOfSystemOutOrSystemErr
    System.out.println(s);
  }

  private static PickleEvent getPickleEvent(TestCase testCase) {
    try {
      Field pickleEventField = TestCase.class.getDeclaredField("pickleEvent");
      pickleEventField.setAccessible(true);
      return (PickleEvent)pickleEventField.get(testCase);
    }
    catch (Exception ignored) {
    }
    return null;
  }

  private static boolean isScenarioOutline(TestCase testCase) {
    PickleEvent pickleEvent = getPickleEvent(testCase);
    return pickleEvent != null && pickleEvent.pickle.getLocations().size() > 1;
  }

  private static int getScenarioOutlineLine(TestCase testCase) {
    PickleEvent pickleEvent = getPickleEvent(testCase);
    if (pickleEvent != null) {
      return pickleEvent.pickle.getLocations().get(pickleEvent.pickle.getLocations().size() - 1).getLine();
    }
    return 0;
  }

  private static String getScenarioName(TestCase testCase) {
    if (isScenarioOutline(testCase)) {
      return SCENARIO_OUTLINE_CAPTION + testCase.getLine();
    }
    return escape(testCase.getName());
  }
}
