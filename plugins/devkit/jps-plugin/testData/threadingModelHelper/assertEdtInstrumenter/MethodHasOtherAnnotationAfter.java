package main;

import com.intellij.util.concurrency.annotations.fake.RequiresEdt;

public class MethodHasOtherAnnotationAfter {
  @RequiresEdt
  @Deprecated
  public Object test() {
    return null;
  }
}