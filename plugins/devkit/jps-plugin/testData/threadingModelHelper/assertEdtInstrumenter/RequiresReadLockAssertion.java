package main;

import com.intellij.util.concurrency.annotations.fake.RequiresReadLock;

public class RequiresReadLockAssertion {
  @RequiresReadLock
  public Object test() {
    return null;
  }
}