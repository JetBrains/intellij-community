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

  public static class IdeaTestSourceReadEvent {
    private final String myUri;

    private final String mySource;

    public IdeaTestSourceReadEvent(String uri, String source) {
      myUri = uri;
      mySource = source;
    }

    public String getUri() {
      return myUri;
    }

    public String getSource() {
      return mySource;
    }
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
