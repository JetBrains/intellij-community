package main;

import com.intellij.util.concurrency.annotations.fake.RequiresEdt;

public class SecondMethod {
  public Object foo() {
    return null;
  }

  @RequiresEdt
  public Object test() {
    return null;
  }
}