package org.jetbrains.plugins.cucumber.java.run;

import cucumber.api.Plugin;
import cucumber.api.event.*;

@SuppressWarnings("unused")
public class CucumberJvm4SMFormatter implements ConcurrentEventListener, Plugin {
  private CucumberJvm3SMFormatter cucumberJvm3SMFormatter = new CucumberJvm3SMFormatter();

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    cucumberJvm3SMFormatter.setEventPublisher(publisher);
  }

  public String getEventUri(TestCaseStarted event) {
    return cucumberJvm3SMFormatter.getEventUri(event);
  }

  public int getEventLine(TestCaseStarted event) {
    return cucumberJvm3SMFormatter.getEventLine(event);
  }

  public String getEventName(TestCaseStarted event) {
    return cucumberJvm3SMFormatter.getEventName(event);
  }

  public String getScenarioName(TestCaseStarted testCaseStarted) {
    return cucumberJvm3SMFormatter.getScenarioName(testCaseStarted);
  }

  public String getScenarioName(TestCaseFinished testCaseFinished) {
    return cucumberJvm3SMFormatter.getScenarioName(testCaseFinished);
  }

  public String getStepLocation(TestStepStarted testStepStarted) {
    return cucumberJvm3SMFormatter.getStepLocation(testStepStarted);
  }

  public String getStepLocation(TestStepFinished testStepFinished) {
    return cucumberJvm3SMFormatter.getStepLocation(testStepFinished);
  }

  public String getStepName(TestStepStarted testStepStarted) {
    return cucumberJvm3SMFormatter.getStepName(testStepStarted);
  }

  public String getStepName(TestStepFinished testStepFinished) {
    return cucumberJvm3SMFormatter.getStepName(testStepFinished);
  }
}
