package org.jetbrains.plugins.cucumber.java.run;

import cucumber.api.HookTestStep;
import cucumber.api.PickleStepTestStep;
import cucumber.api.TestCase;
import cucumber.api.TestStep;
import cucumber.api.event.TestCaseFinished;
import cucumber.api.event.TestCaseStarted;
import cucumber.api.event.TestStepFinished;
import cucumber.api.event.TestStepStarted;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatterUtil.FILE_RESOURCE_PREFIX;
import static org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatterUtil.escape;

@SuppressWarnings("unused")
public class CucumberJvm3SMFormatter extends CucumberJvm2SMFormatter {
  public CucumberJvm3SMFormatter() {
    super();
  }

  @Override
  protected String getEventUri(TestCaseStarted event) {
    return event.testCase.getUri();
  }

  @Override
  protected int getEventLine(TestCaseStarted event) {
    return event.testCase.getLine();
  }

  @Override
  protected String getEventName(TestCaseStarted event) {
    return event.testCase.getName();
  }

  @Override
  protected String getScenarioName(TestCaseStarted testCaseStarted) {
    return getScenarioName(testCaseStarted.testCase);
  }

  @Override
  protected String getScenarioName(TestCaseFinished testCaseFinished) {
    return getScenarioName(testCaseFinished.testCase);
  }

  @Override
  protected String getStepLocation(TestStepStarted testStepStarted) {
    return getStepLocation(testStepStarted.testStep);
  }

  @Override
  protected String getStepLocation(TestStepFinished testStepFinished) {
    return getStepLocation(testStepFinished.testStep);
  }

  @Override
  protected String getStepName(TestStepStarted testStepStarted) {
    return getStepName(testStepStarted.testStep);
  }

  @Override
  protected String getStepName(TestStepFinished testStepFinished) {
    return getStepName(testStepFinished.testStep);
  }

  private static String getScenarioName(TestCase testCase) {
    return escape("Scenario: " + testCase.getName());
  }

  private static String getStepLocation(TestStep step) {
    if (step instanceof HookTestStep) {
      try {
        Field definitionMatchField = step.getClass().getSuperclass().getDeclaredField("stepDefinitionMatch");
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
    PickleStepTestStep pickleStepTestStep = (PickleStepTestStep) step;
    return FILE_RESOURCE_PREFIX + pickleStepTestStep.getStepLocation() + ":" + pickleStepTestStep.getStepLine();
  }

  private static String getStepName(TestStep step) {
    String stepName;
    if (step instanceof HookTestStep) {
      stepName = "Hook: " + ((HookTestStep)step).getHookType().toString();
    } else {
      stepName = ((PickleStepTestStep) step).getPickleStep().getText();
    }
    return escape(stepName);
  }
}
