package main;

import com.intellij.util.concurrency.annotations.fake.RequiresEdt;

public class MethodHasOtherAnnotationBefore {
  @Deprecated
  @RequiresEdt
  public Object test() {
    return null;
  }
}