package main;

import com.intellij.util.concurrency.annotations.fake.RequiresNoReadLock;

public class RequiresNoReadLockAssertion {
  @RequiresNoReadLock
  public Object test() {
    return null;
  }
}