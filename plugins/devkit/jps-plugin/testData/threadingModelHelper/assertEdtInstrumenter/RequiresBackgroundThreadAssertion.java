package main;

import com.intellij.util.concurrency.annotations.fake.RequiresBackgroundThread;

public class RequiresBackgroundThreadAssertion {
  @RequiresBackgroundThread
  public Object test() {
    return null;
  }
}