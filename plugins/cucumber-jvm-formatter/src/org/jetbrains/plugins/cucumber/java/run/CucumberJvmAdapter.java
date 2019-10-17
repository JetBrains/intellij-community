// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.run;

public class CucumberJvmAdapter {
  interface IdeaTestCaseEvent {
    IdeaTestCase getTestCase();
    String getUri();
  }

  interface IdeaTestStepEvent {
    IdeaTestStep getTestStep();
  }

  interface IdeaTestStepFinishedEvent extends IdeaTestStepEvent {
    enum Status {PASSED, PENDING, SKIPPED, FAILED, }
    Status getResult();
    Long getDuration();
    String getErrorMessage();
  }

  public static class IdeaWriteEvent {
    private final String myText;

    public IdeaWriteEvent(String text) {
      myText = text;
    }

    public String getText() {
      return myText;
    }
  }

  interface IdeaTestSourceReadEvent {
    String getUri();

    String getSource();
  }

  interface IdeaTestCase {
    boolean isScenarioOutline();
    String getUri();
    int getScenarioOutlineLine();
    int getLine();
    String getScenarioName();
  }

  interface IdeaTestStep {
    String getLocation();
    String getStepName();
  }
}
