package main;

import com.intellij.util.concurrency.annotations.fake.RequiresEdt;

public class Simple {
  @RequiresEdt
  public Object test() {
    return null;
  }
}