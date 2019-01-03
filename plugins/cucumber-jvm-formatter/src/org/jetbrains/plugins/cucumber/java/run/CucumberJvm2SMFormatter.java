package org.jetbrains.plugins.cucumber.java.run;

import cucumber.api.TestCase;
import cucumber.api.TestStep;
import cucumber.api.event.*;
import cucumber.api.formatter.Formatter;
import gherkin.events.PickleEvent;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static cucumber.api.Result.Type.*;
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
    outCommand(String.format(TEMPLATE_ENTER_THE_MATRIX, getCurrentTime()));
    outCommand(String.format(TEMPLATE_SCENARIO_COUNTING_STARTED, 0, getCurrentTime()));
  }

  private final EventHandler<TestCaseStarted> testCaseStartedHandler = new EventHandler<TestCaseStarted>() {
    @Override
    public void receive(TestCaseStarted event) {
      CucumberJvm2SMFormatter.this.handleTestCaseStarted(event);
    }
  };

  private final EventHandler<TestCaseFinished> testCaseFinishedHandler = new EventHandler<TestCaseFinished>() {
    @Override
    public void receive(TestCaseFinished event) {
      handleTestCaseFinished(event);
    }
  };

  private final EventHandler<TestRunFinished> testRunFinishedHandler = new EventHandler<TestRunFinished>() {
    @Override
    public void receive(TestRunFinished event) {
      CucumberJvm2SMFormatter.this.handleTestRunFinished(event);
    }
  };

  private final EventHandler<TestStepStarted> testStepStartedHandler = new EventHandler<TestStepStarted>() {
    @Override
    public void receive(TestStepStarted event) {
      handleTestStepStarted(event);
    }
  };

  private final EventHandler<TestStepFinished> testStepFinishedHandler = new EventHandler<TestStepFinished>() {
    @Override
    public void receive(TestStepFinished event) {
      handleTestStepFinished(event);
    }
  };

  private final EventHandler<TestSourceRead> testSourceReadHandler = new EventHandler<TestSourceRead>() {
    @Override
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
      outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), escape(getEventUri(event)),
                               escape(getFeatureFileDescription(getEventUri(event)))));
    }
    else if (!getEventUri(event).equals(currentFilePath)) {
      closeCurrentScenarioOutline();
      outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(),
                               escape(getFeatureFileDescription(currentFilePath))));
      outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), escape(getEventUri(event)),
                               escape(getFeatureFileDescription(getEventUri(event)))));
    }

    outCommand(String.format(TEMPLATE_SCENARIO_STARTED, getCurrentTime()));

    if (isScenarioOutline(event.testCase)) {
      int mainScenarioLine = getScenarioOutlineLine(event.testCase);
      if (currentScenarioOutlineLine != mainScenarioLine || currentFilePath == null ||
          !currentFilePath.equals(getEventUri(event))) {
        closeCurrentScenarioOutline();
        currentScenarioOutlineLine = mainScenarioLine;
        currentScenarioOutlineName = getEventName(event);
        outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(),
                                 escape(getEventUri(event)) + ":" + currentScenarioOutlineLine, escape(currentScenarioOutlineName)));
        outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), "", EXAMPLES_CAPTION));
      }
    } else {
      closeCurrentScenarioOutline();
    }
    currentFilePath = getEventUri(event);

    outCommand(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(),
                             escape(getEventUri(event)) + ":" + getEventLine(event), escape(getScenarioName(event))));
  }

  private void handleTestCaseFinished(TestCaseFinished event) {
    outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), escape(getScenarioName(event))));
    outCommand(String.format(TEMPLATE_SCENARIO_FINISHED, getCurrentTime()));
  }

  private void handleTestRunFinished(TestRunFinished event) {
    closeCurrentScenarioOutline();
    outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(),
                             escape(getFeatureFileDescription(currentFilePath))));
  }
  private void handleTestStepStarted(TestStepStarted event) {
    outCommand(String.format(TEMPLATE_TEST_STARTED, getCurrentTime(), escape(getStepLocation(event)),
                             escape(getStepName(event))));
  }

  private void handleTestStepFinished(TestStepFinished event) {
    if (event.result.getStatus() == PASSED) {
      // write nothing
    } else if (event.result.getStatus() == SKIPPED || event.result.getStatus() == PENDING) {
      outCommand(String.format(TEMPLATE_TEST_PENDING, escape(getStepName(event)), getCurrentTime()));
    } else {
      outCommand(String.format(TEMPLATE_TEST_FAILED, getCurrentTime(), "",
                               escape(event.result.getErrorMessage()), escape(getStepName(event)), ""));
    }
    Long duration = event.result.getDuration() != null ? event.result.getDuration() / 1000000: 0;
    outCommand(String.format(TEMPLATE_TEST_FINISHED, getCurrentTime(), duration, escape(getStepName(event))));
  }

  private String getFeatureFileDescription(String uri) {
    if (pathToDescription.containsKey(uri)) {
      return pathToDescription.get(uri);
    }
    return uri;
  }

  private void handleTestSourceRead(TestSourceRead event) {
    closeCurrentScenarioOutline();
    pathToDescription.put(event.uri, getFeatureName(event.source));
  }

  private void closeCurrentScenarioOutline() {
    if (currentScenarioOutlineLine > 0) {
      outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), EXAMPLES_CAPTION));
      outCommand(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), escape(currentScenarioOutlineName)));
      currentScenarioOutlineLine = 0;
      currentScenarioOutlineName = null;
    }
  }

  private static String getStepLocation(TestStep step) {
    if (step.isHook()) {
      try {
        Field definitionMatchField = step.getClass().getSuperclass().getDeclaredField("definitionMatch");
        definitionMatchField.setAccessible(true);
        Object definitionMatchFieldValue = definitionMatchField.get(step);

        Field hookDefinitionField = definitionMatchFieldValue.getClass().getDeclaredField("hookDefinition");
        hookDefinitionField.setAccessible(true);
        Object hookDefinitionFieldValue = hookDefinitionField.get(definitionMatchFieldValue);

        Field methodField = hookDefinitionFieldValue.getClass().getDeclaredField("method");
        methodField.setAccessible(true);
        Object methodFieldValue = methodField.get(hookDefinitionFieldValue);
        if (methodFieldValue instanceof Method) {
          Method method = (Method)methodFieldValue;
          return String.format("java:test://%s/%s", method.getDeclaringClass().getName(), method.getName());
        }
      }
      catch (Exception ignored) {
      }
      return "";
    }
    return FILE_RESOURCE_PREFIX + step.getStepLocation() + ":" + step.getStepLine();
  }

  private static String getStepName(TestStep step) {
    String stepName;
    if (step.isHook()) {
      stepName = "Hook: " + step.getHookType().toString();
    } else {
      stepName = step.getStepText();
    }
    return stepName;
  }

  private void outCommand(String s) {
    myOut.println(s);
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
    return testCase.getName();
  }

  protected String getScenarioName(TestCaseStarted testCaseStarted) {
    return getScenarioName(testCaseStarted.testCase);
  }

  protected String getScenarioName(TestCaseFinished testCaseFinished) {
    return getScenarioName(testCaseFinished.testCase);
  }

  protected String getEventUri(TestCaseStarted event) {
    return event.testCase.getUri();
  }

  protected int getEventLine(TestCaseStarted event) {
    return event.testCase.getLine();
  }

  protected String getEventName(TestCaseStarted event) {
    return event.testCase.getName();
  }

  protected String getStepLocation(TestStepStarted testStepStarted) {
    return getStepLocation(testStepStarted.testStep);
  }

  protected String getStepLocation(TestStepFinished testStepFinished) {
    return getStepLocation(testStepFinished.testStep);
  }

  protected String getStepName(TestStepStarted testStepStarted) {
    return getStepName(testStepStarted.testStep);
  }

  protected String getStepName(TestStepFinished testStepFinished) {
    return getStepName(testStepFinished.testStep);
  }

  private String getCurrentTime() {
    if (myCurrentTimeValue != null) {
      return myCurrentTimeValue;
    }
    return CucumberJvmSMFormatterUtil.getCurrentTime();
  }
}
