/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diff.impl;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.util.ArrayList;

public class MultiCheck {
  private final ArrayList<FailedCondition> myFailedConditions = new ArrayList<>();

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
