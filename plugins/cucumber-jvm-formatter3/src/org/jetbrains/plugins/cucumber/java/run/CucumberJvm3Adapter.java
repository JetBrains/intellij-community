// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.run;

import cucumber.api.HookTestStep;
import cucumber.api.PickleStepTestStep;
import cucumber.api.TestCase;
import cucumber.api.TestStep;
import cucumber.api.event.*;
import gherkin.events.PickleEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatterUtil.FILE_RESOURCE_PREFIX;

public class CucumberJvm3Adapter {
  public static class IdeaTestCaseEvent implements CucumberJvmAdapter.IdeaTestCaseEvent {
    private final IdeaTestCase myTestCase;

    public IdeaTestCaseEvent(TestCaseStarted testCaseStarted) {
      myTestCase = new IdeaTestCase(testCaseStarted.testCase);
    }

    public IdeaTestCaseEvent(TestCaseFinished testCaseFinished) {
      myTestCase = new IdeaTestCase(testCaseFinished.testCase);
    }

    @Override
    public CucumberJvmAdapter.IdeaTestCase getTestCase() {
      return myTestCase;
    }

    @Override
    public String getUri() {
      return myTestCase.getUri();
    }
  }

  public static class IdeaTestStepEvent implements CucumberJvmAdapter.IdeaTestStepEvent {
    private final IdeaTestStep myTestStep;

    public IdeaTestStepEvent(TestStepStarted testStepStarted) {
      this(testStepStarted.testStep);
    }

    public IdeaTestStepEvent(TestStep testStep) {
      myTestStep = new IdeaTestStep(testStep);
    }

    @Override
    public CucumberJvmAdapter.IdeaTestStep getTestStep() {
      return myTestStep;
    }
  }

  public static class IdeaTestStepFinishedEvent extends IdeaTestStepEvent implements CucumberJvmAdapter.IdeaTestStepFinishedEvent {
    TestStepFinished myRealEvent;

    public IdeaTestStepFinishedEvent(TestStepFinished testStepFinished) {
      super(testStepFinished.testStep);
      myRealEvent = testStepFinished;
    }

    @Override
    public Status getResult() {
      switch (myRealEvent.result.getStatus()) {
        case PASSED: return Status.PASSED;
        case PENDING: return Status.PENDING;
        case SKIPPED: return Status.SKIPPED;
        default: return Status.FAILED;
      }
    }

    @Override
    public Long getDuration() {
      return myRealEvent.result.getDuration() != null ? myRealEvent.result.getDuration() / 1000000: 0;
    }

    @Override
    public String getErrorMessage() {
      return myRealEvent.result.getErrorMessage();
    }
  }

  public static class IdeaTestCase implements CucumberJvmAdapter.IdeaTestCase {
    private final TestCase myRealTestCase;

    IdeaTestCase(TestCase realTestCase) {
      myRealTestCase = realTestCase;
    }

    @Override
    public boolean isScenarioOutline() {
      PickleEvent pickleEvent = getPickleEvent(myRealTestCase);
      return pickleEvent != null && pickleEvent.pickle.getLocations().size() > 1;
    }

    @Override
    public String getUri() {
      return myRealTestCase.getUri();
    }

    @Override
    public int getScenarioOutlineLine() {
      PickleEvent pickleEvent = getPickleEvent(myRealTestCase);
      if (pickleEvent != null) {
        return pickleEvent.pickle.getLocations().get(pickleEvent.pickle.getLocations().size() - 1).getLine();
      }
      return 0;
    }

    @Override
    public int getLine() {
      return myRealTestCase.getLine();
    }

    @Override
    public String getScenarioName() {
      return "Scenario: " + myRealTestCase.getName();
    }
  }

  public static class IdeaTestStep implements CucumberJvmAdapter.IdeaTestStep {
    private final TestStep myRealStep;

    public IdeaTestStep(TestStep realStep) {
      myRealStep = realStep;
    }

    @Override
    public String getLocation() {
      if (myRealStep instanceof HookTestStep) {
        try {
          Field definitionMatchField = myRealStep.getClass().getSuperclass().getDeclaredField("stepDefinitionMatch");
          definitionMatchField.setAccessible(true);
          Object definitionMatchFieldValue = definitionMatchField.get(myRealStep);

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
      PickleStepTestStep pickleStepTestStep = (PickleStepTestStep) myRealStep;
      return FILE_RESOURCE_PREFIX + pickleStepTestStep.getStepLocation() + ":" + pickleStepTestStep.getStepLine();
    }

    @Override
    public String getStepName() {
      String stepName;
      if (myRealStep instanceof HookTestStep) {
        stepName = "Hook: " + ((HookTestStep)myRealStep).getHookType().toString();
      } else {
        stepName = ((PickleStepTestStep) myRealStep).getPickleStep().getText();
      }
      return stepName;
    }
  }

  private static PickleEvent getPickleEvent(TestCase testCase) {
    try {
      Field pickleEventField = testCase.getClass().getDeclaredField("pickleEvent");
      pickleEventField.setAccessible(true);
      return (PickleEvent)pickleEventField.get(testCase);
    }
    catch (Exception ignored) {
    }
    return null;
  }
}
