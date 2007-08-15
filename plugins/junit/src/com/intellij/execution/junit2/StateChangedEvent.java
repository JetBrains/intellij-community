package com.intellij.execution.junit2;

import com.intellij.execution.testframework.AbstractTestProxy;

public class StateChangedEvent extends TestEvent {
  public StateChangedEvent(final TestProxy test) {
    super(test);
  }

  public AbstractTestProxy getTestSubtree() {
    final TestProxy test = getSource();
    final AbstractTestProxy parent = test.getParent();
    return parent != null ? parent : test;
  }
}
