package com.intellij.execution.junit2;

import com.intellij.execution.testframework.AbstractTestProxy;

public class TestEvent {
  private final TestProxy mySource;

  public TestEvent(final TestProxy source) {
    mySource = source;
  }

  public TestProxy getSource() {
    return mySource;
  }

  public int hashCode() {
    return mySource.hashCode();
  }

  public boolean equals(final Object obj) {
    if (obj == null)
      return false;
    if (mySource != ((TestEvent) obj).mySource) return false;
    return obj.getClass() == getClass();
  }

  public AbstractTestProxy getTestSubtree() {
    return null;
  }
}
