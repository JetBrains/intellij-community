package com.intellij.openapi.diff.impl;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.util.ArrayList;

public class MultiCheck {
  private final ArrayList<FailedCondition> myFailedConditions = new ArrayList<FailedCondition> ();

  public void assertEquals(int expected, int actual) {
    if (expected != actual)
      myFailedConditions.add(new BooleanCondition(expected + "==" + actual));
  }

  public void flush() {
    if (myFailedConditions.size() == 0) return;
    for (FailedCondition condition: myFailedConditions) {
      try {
        condition.fail();
      }
      catch (AssertionFailedError e) {
        e.printStackTrace(System.err);
      }
    }
    Assert.fail();
  }

  public void assertNull(Object object) {
    if (object != null) myFailedConditions.add(new BooleanCondition("Expected null: " + object));
  }

  private static abstract class FailedCondition {
    abstract void fail();
  }

  private static class BooleanCondition extends FailedCondition {
    private final AssertionFailedError myFailure;

    public BooleanCondition(String message) {
      myFailure = new AssertionFailedError(message);
    }

    @Override
    public void fail() {
      throw myFailure;
    }
  }
}
