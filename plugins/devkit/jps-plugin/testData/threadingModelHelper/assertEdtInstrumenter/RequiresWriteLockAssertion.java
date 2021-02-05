package main;

import com.intellij.util.concurrency.annotations.fake.RequiresWriteLock;

public class RequiresWriteLockAssertion {
  @RequiresWriteLock
  public Object test() {
    return null;
  }
}