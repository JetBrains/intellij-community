package main;

import com.intellij.util.concurrency.annotations.fake.RequiresEdt;

public class LineNumberWhenBodyHasTwoStatements {
  @RequiresEdt
  public Object test() {
    Object o = new Object();
    return o;
  }
}