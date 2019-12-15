// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.run;

import cucumber.api.TestCase;
import cucumber.api.TestStep;
import cucumber.api.event.*;
import gherkin.events.PickleEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatterUtil.FILE_RESOURCE_PREFIX;

public class CucumberJvm2Adapter {
  public static class CucumberJvmTestStepFinishedEvent implements org.jetbrains.plugins.cucumber.java.run.CucumberJvmTestStepFinishedEvent {
    private final CucumberJvmTestStep myTestStep;
    private final TestStepFinished myRealEvent;

    public CucumberJvmTestStepFinishedEvent(TestStepFinished testStepFinished) {
      myTestStep = new CucumberJvmTestStep(testStepFinished.testStep);
      myRealEvent = testStepFinished;
    }

    @Override
    public org.jetbrains.plugins.cucumber.java.run.CucumberJvmTestStep getTestStep() {
      return myTestStep;
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

  public static class CucumberJvmTestCase implements org.jetbrains.plugins.cucumber.java.run.CucumberJvmTestCase {
    private final TestCase myRealTestCase;

    CucumberJvmTestCase(TestCase realTestCase) {
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
      return myRealTestCase.getName();
    }
  }

  public static class CucumberJvmTestStep implements org.jetbrains.plugins.cucumber.java.run.CucumberJvmTestStep {
    private final TestStep myRealStep;

    public CucumberJvmTestStep(TestStep realStep) {
      myRealStep = realStep;
    }

    @Override
    public String getLocation() {
      if (myRealStep.isHook()) {
        try {
          Field definitionMatchField = myRealStep.getClass().getSuperclass().getDeclaredField("definitionMatch");
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
      return FILE_RESOURCE_PREFIX + myRealStep.getStepLocation() + ":" + myRealStep.getStepLine();
    }

    @Override
    public String getStepName() {
      String stepName;
      if (myRealStep.isHook()) {
        stepName = "Hook: " + myRealStep.getHookType().toString();
      } else {
        stepName = getStepKeyword() + " " + myRealStep.getStepText();
      }
      return stepName;
    }

    private String getStepKeyword() {
      try {
        String filePath = myRealStep.getStepLocation().substring(0, myRealStep.getStepLocation().indexOf(':'));
        String line = CucumberJvmSMFormatterUtil.getStepKeyword(filePath, myRealStep.getStepLine());
        if (line != null) {
          return line;
        }
      }
      catch (Throwable ignored) {
      }
      return "Given";
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
