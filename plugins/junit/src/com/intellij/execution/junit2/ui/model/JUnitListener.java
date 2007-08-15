package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.junit2.TestEvent;
import com.intellij.execution.junit2.TestProxy;

import java.util.List;

public interface JUnitListener {
  void onTestSelected(TestProxy test);
  void onDispose(JUnitRunningModel model);
  void onTestChanged(TestEvent event);
  void onRunnerStateChanged(StateEvent event);
  void onEventsDispatched(List<TestEvent> events);
}
