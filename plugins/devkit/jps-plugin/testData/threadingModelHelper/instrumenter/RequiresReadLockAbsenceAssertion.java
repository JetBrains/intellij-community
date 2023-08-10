package main;

import com.intellij.util.concurrency.annotations.fake.RequiresReadLockAbsence;

public class RequiresReadLockAbsenceAssertion {
  @RequiresReadLockAbsence
  public Object test() {
    return null;
  }
}