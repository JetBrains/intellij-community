package main;

import com.intellij.util.concurrency.annotations.fake.RequiresEdt;

public class LineNumberWhenOtherMethodBefore {
  public Object someMethod() {
    return null;
  }

  @RequiresEdt
  public Object test() {
    return null;
  }
}