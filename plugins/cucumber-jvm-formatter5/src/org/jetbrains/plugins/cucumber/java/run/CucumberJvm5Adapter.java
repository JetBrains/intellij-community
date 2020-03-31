// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.run;

import gherkin.pickles.Pickle;
import io.cucumber.plugin.event.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatterUtil.FILE_RESOURCE_PREFIX;

public class CucumberJvm5Adapter {
  public static class CucumberJvmTestStepFinishedEvent implements org.jetbrains.plugins.cucumber.java.run.CucumberJvmTestStepFinishedEvent {
    private final CucumberJvmTestStep myTestStep;
    private final TestStepFinished myRealEvent;

    public CucumberJvmTestStepFinishedEvent(TestStepFinished testStepFinished) {
      myTestStep = new CucumberJvmTestStep(testStepFinished.getTestStep());
      myRealEvent = testStepFinished;
    }

    @Override
    public org.jetbrains.plugins.cucumber.java.run.CucumberJvmTestStep getTestStep() {
      return myTestStep;
    }

    @Override
    public Status getResult() {
      switch (myRealEvent.getResult().getStatus()) {
        case PASSED: return Status.PASSED;
        case PENDING: return Status.PENDING;
        case SKIPPED: return Status.SKIPPED;
        default: return Status.FAILED;
      }
    }

    @Override
    public Long getDuration() {
      return myRealEvent.getResult().getDuration() != null ? myRealEvent.getResult().getDuration().toMillis(): 0;
    }

    @Override
    public String getErrorMessage() {
      Throwable error = myRealEvent.getResult().getError();
      String result = error != null ? error.getMessage() : null;
      return result != null ? result : "";
    }
  }

  public static class CucumberJvmTestCase implements org.jetbrains.plugins.cucumber.java.run.CucumberJvmTestCase {
    private final TestCase myRealTestCase;

    CucumberJvmTestCase(TestCase realTestCase) {
      myRealTestCase = realTestCase;
    }

    @Override
    public boolean isScenarioOutline() {
      Pickle pickle = getPickleEvent(myRealTestCase);
      return pickle != null && pickle.getLocations().size() > 1;
    }

    @Override
    public String getUri() {
      return myRealTestCase.getUri().getPath();
    }

    @Override
    public int getScenarioOutlineLine() {
      Pickle pickle = getPickleEvent(myRealTestCase);
      if (pickle != null) {
        return pickle.getLocations().get(pickle.getLocations().size() - 1).getLine();
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
      return FILE_RESOURCE_PREFIX + pickleStepTestStep.getCodeLocation() + ":" + pickleStepTestStep.getStepLine();
    }

    @Override
    public String getStepName() {
      String stepName;
      if (myRealStep instanceof HookTestStep) {
        stepName = "Hook: " + ((HookTestStep)myRealStep).getHookType().toString();
      } else {
        PickleStepTestStep pickleStep = (PickleStepTestStep)myRealStep;
        stepName = pickleStep.getStep().getKeyWord() + pickleStep.getStepText();
      }
      return stepName;
    }
  }

  private static Pickle getPickleEvent(TestCase testCase) {
    try {
      Field cucumberPickleField = testCase.getClass().getDeclaredField("pickle");
      cucumberPickleField.setAccessible(true);
      Object cucumberPickle = cucumberPickleField.get(testCase);
      Field pickleField = cucumberPickle.getClass().getDeclaredField("pickle");
      pickleField.setAccessible(true);
      return (Pickle) pickleField.get(cucumberPickle);
    }
    catch (Exception ignored) {
    }
    return null;
  }
}
