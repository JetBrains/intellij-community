package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.junit2.TestEvent;
import com.intellij.execution.junit2.TestProxy;

import java.util.List;

public abstract class JUnitAdapter implements JUnitListener {

  public void onTestSelected(final TestProxy test) {
  }

  public final void onDispose(final JUnitRunningModel model) {
    model.removeListener(this);
    doDispose();
  }

  protected void doDispose() {}

  public void onTestChanged(final TestEvent event) {
  }

  public void onRunnerStateChanged(final StateEvent event) {
  }

  public void onEventsDispatched(final List<TestEvent> events) {
  }
}
