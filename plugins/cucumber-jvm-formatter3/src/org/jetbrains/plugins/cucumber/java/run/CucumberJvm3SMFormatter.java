package org.jetbrains.plugins.cucumber.java.run;

import cucumber.api.event.*;
import cucumber.api.formatter.Formatter;

@SuppressWarnings({"unused", "Convert2Lambda"})
public class CucumberJvm3SMFormatter extends CucumberJvmSMConverter implements Formatter {
  public CucumberJvm3SMFormatter() {
    super();
  }

  private final EventHandler<TestCaseStarted> testCaseStartedHandler = new EventHandler<TestCaseStarted>() {
    @Override
    public void receive(TestCaseStarted event) {
      CucumberJvm3SMFormatter.this.handleTestCaseStarted(new CucumberJvm3Adapter.CucumberJvmTestCase(event.testCase));
    }
  };

  private final EventHandler<TestCaseFinished> testCaseFinishedHandler = new EventHandler<TestCaseFinished>() {
    @Override
    public void receive(TestCaseFinished event) {
      handleTestCaseFinished(new CucumberJvm3Adapter.CucumberJvmTestCase(event.testCase));
    }
  };

  private final EventHandler<TestRunFinished> testRunFinishedHandler = new EventHandler<TestRunFinished>() {
    @Override
    public void receive(TestRunFinished event) {
      CucumberJvm3SMFormatter.this.handleTestRunFinished();
    }
  };

  private final EventHandler<WriteEvent> writeEventHandler = new EventHandler<WriteEvent>() {
    @Override
    public void receive(WriteEvent event) {
      CucumberJvm3SMFormatter.this.handleWriteEvent(new CucumberJvmWriteEvent(event.text));
    }
  };

  private final EventHandler<TestStepStarted> testStepStartedHandler = new EventHandler<TestStepStarted>() {
    @Override
    public void receive(TestStepStarted event) {
      handleTestStepStarted(new CucumberJvm3Adapter.CucumberJvmTestStep(event.testStep));
    }
  };

  private final EventHandler<TestStepFinished> testStepFinishedHandler = new EventHandler<TestStepFinished>() {
    @Override
    public void receive(TestStepFinished event) {
      handleTestStepFinished(new CucumberJvm3Adapter.CucumberJvmTestStepFinishedEvent(event));
    }
  };

  private final EventHandler<TestSourceRead> testSourceReadHandler = new EventHandler<TestSourceRead>() {
    @Override
    public void receive(TestSourceRead event) {
      CucumberJvm3SMFormatter.this.handleTestSourceRead(new CucumberJvmTestSourceReadEvent(event.uri, event.source));
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
    publisher.registerHandlerFor(WriteEvent.class, this.writeEventHandler);
  }
}
