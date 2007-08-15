package com.intellij.execution.junit2;

public class NewChildEvent extends TestEvent {
  private final TestProxy myChild;

  public TestProxy getChild() {
    return myChild;
  }

  public NewChildEvent(final TestProxy parent, final TestProxy child) {
    super(parent);
    myChild = child;
  }

  public boolean equals(final Object obj) {
    return super.equals(obj) && ((NewChildEvent) obj).myChild == myChild;
  }

  public int hashCode() {
    return super.hashCode() ^ myChild.hashCode();
  }

  public TestProxy getTestSubtree() {
    return getSource();
  }
}
