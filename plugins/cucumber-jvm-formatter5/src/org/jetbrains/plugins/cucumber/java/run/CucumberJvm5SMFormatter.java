// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.run;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;

import java.io.PrintStream;

@SuppressWarnings({"unused", "Convert2Lambda"})
public class CucumberJvm5SMFormatter extends CucumberJvmSMConverter implements ConcurrentEventListener {
  public CucumberJvm5SMFormatter() {
    //noinspection UseOfSystemOutOrSystemErr
    this(System.out, null);
  }

  public CucumberJvm5SMFormatter(PrintStream out, String currentTimeValue) {
    super(out, currentTimeValue);
  }

  private final EventHandler<TestCaseStarted> testCaseStartedHandler = new EventHandler<TestCaseStarted>() {
    @Override
    public void receive(TestCaseStarted event) {
      CucumberJvm5SMFormatter.this.handleTestCaseStarted(new CucumberJvm5Adapter.CucumberJvmTestCase(event.getTestCase()));
    }
  };

  private final EventHandler<TestCaseFinished> testCaseFinishedHandler = new EventHandler<TestCaseFinished>() {
    @Override
    public void receive(TestCaseFinished event) {
      handleTestCaseFinished(new CucumberJvm5Adapter.CucumberJvmTestCase(event.getTestCase()));
    }
  };

  private final EventHandler<TestRunFinished> testRunFinishedHandler = new EventHandler<TestRunFinished>() {
    @Override
    public void receive(TestRunFinished event) {
      CucumberJvm5SMFormatter.this.handleTestRunFinished();
    }
  };

  private final EventHandler<WriteEvent> writeEventHandler = new EventHandler<WriteEvent>() {
    @Override
    public void receive(WriteEvent event) {
      CucumberJvm5SMFormatter.this.handleWriteEvent(new CucumberJvmWriteEvent(event.getText()));
    }
  };

  private final EventHandler<TestStepStarted> testStepStartedHandler = new EventHandler<TestStepStarted>() {
    @Override
    public void receive(TestStepStarted event) {
      handleTestStepStarted(new CucumberJvm5Adapter.CucumberJvmTestStep(event.getTestStep()));
    }
  };

  private final EventHandler<TestStepFinished> testStepFinishedHandler = new EventHandler<TestStepFinished>() {
    @Override
    public void receive(TestStepFinished event) {
      handleTestStepFinished(new CucumberJvm5Adapter.CucumberJvmTestStepFinishedEvent(event));
    }
  };

  private final EventHandler<TestSourceRead> testSourceReadHandler = new EventHandler<TestSourceRead>() {
    @Override
    public void receive(TestSourceRead event) {
      CucumberJvm5SMFormatter.this
        .handleTestSourceRead(new CucumberJvmTestSourceReadEvent(event.getUri().getPath(), event.getSource()));
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
