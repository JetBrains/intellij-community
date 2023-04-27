// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.run;

import com.intellij.junit4.ExpectedPatterns;
import com.intellij.rt.execution.junit.ComparisonFailureData;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatterUtil.*;

public class CucumberJvmSMConverter {
  private static final String EXAMPLES_CAPTION = "Examples:";
  private static final String SCENARIO_OUTLINE_CAPTION = "Scenario: Line: ";
  private final Map<String, String> pathToDescription = new HashMap<>();
  private String currentFilePath;
  private int currentScenarioOutlineLine;
  private String currentScenarioOutlineName;
  private final PrintStream myOut;
  private final String myCurrentTimeValue;

  public CucumberJvmSMConverter() {
    //noinspection UseOfSystemOutOrSystemErr
    this(System.out, null);
  }

  public CucumberJvmSMConverter(PrintStream out, String currentTimeValue) {
    myOut = out;
    myCurrentTimeValue = currentTimeValue;
    outCommand(TEMPLATE_ENTER_THE_MATRIX, getCurrentTime());
    outCommand(TEMPLATE_SCENARIO_COUNTING_STARTED, "0", getCurrentTime());
  }

  public void handleTestCaseStarted(CucumberJvmTestCase testCase) {
    String uri = testCase.getUri();
    if (currentFilePath == null) {
      outCommand(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), uri, getFeatureFileDescription(uri));
    }
    else if (!uri.equals(currentFilePath)) {
      closeCurrentScenarioOutline();
      outCommand(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), getFeatureFileDescription(currentFilePath));
      outCommand(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), uri, getFeatureFileDescription(uri));
    }

    outCommand(TEMPLATE_SCENARIO_STARTED, getCurrentTime());

    if (testCase.isScenarioOutline()) {
      int mainScenarioLine = testCase.getScenarioOutlineLine();
      if (currentScenarioOutlineLine != mainScenarioLine || currentFilePath == null ||
          !currentFilePath.equals(uri)) {
        closeCurrentScenarioOutline();
        currentScenarioOutlineLine = mainScenarioLine;
        currentScenarioOutlineName = "Scenario Outline: " + testCase.getScenarioName();
        outCommand(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), uri + ":" + currentScenarioOutlineLine,
                   currentScenarioOutlineName);
        outCommand(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), "", EXAMPLES_CAPTION);
      }
    } else {
      closeCurrentScenarioOutline();
    }
    currentFilePath = uri;

    outCommand(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), uri + ":" + testCase.getLine(),
               getScenarioName(testCase));
  }

  public void handleTestCaseFinished(CucumberJvmTestCase testCase) {
    outCommand(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), getScenarioName(testCase));
    outCommand(TEMPLATE_SCENARIO_FINISHED, getCurrentTime());
  }

  public void handleTestRunFinished() {
    closeCurrentScenarioOutline();
    outCommand(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), getFeatureFileDescription(currentFilePath));
  }

  public void handleWriteEvent(CucumberJvmWriteEvent event) {
    myOut.println(event.getText());
  }

  public void handleTestStepStarted(CucumberJvmTestStep testStep) {
    outCommand(TEMPLATE_TEST_STARTED, getCurrentTime(), testStep.getLocation(), testStep.getStepName());
  }

  public void handleTestStepFinished(CucumberJvmTestStepFinishedEvent event) {
    if (event.getResult() == CucumberJvmTestStepFinishedEvent.Status.PASSED) {
      // write nothing
    }
    else if (event.getResult() == CucumberJvmTestStepFinishedEvent.Status.SKIPPED ||
             event.getResult() == CucumberJvmTestStepFinishedEvent.Status.PENDING) {
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

  public void handleTestSourceRead(CucumberJvmTestSourceReadEvent event) {
    closeCurrentScenarioOutline();
    pathToDescription.put(event.getUri(), getFeatureName(event.getSource()));
  }

  private String getFeatureFileDescription(String uri) {
    if (pathToDescription.containsKey(uri)) {
      return pathToDescription.get(uri);
    }
    return uri;
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

  private static String getScenarioName(CucumberJvmTestCase testCase) {
    if (testCase.isScenarioOutline()) {
      return SCENARIO_OUTLINE_CAPTION + testCase.getLine();
    }
    return "Scenario: " + testCase.getScenarioName();
  }

  private String getCurrentTime() {
    if (myCurrentTimeValue != null) {
      return myCurrentTimeValue;
    }
    return CucumberJvmSMFormatterUtil.getCurrentTime();
  }
}
