package main;

import com.intellij.util.concurrency.annotations.fake.RequiresEdt;

public class DoNotInstrument {
  @RequiresEdt(generateAssertion = false)
  public Object test() {
    return null;
  }
}