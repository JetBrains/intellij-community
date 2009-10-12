/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.rt.execution.junit;

import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import java.util.Hashtable;

public class RunOnce extends TestResult {
  private final Hashtable myPeformedTests = new Hashtable();
  private static final String NOT_ALLOWED_IN_ID = ":";

  protected void run(TestCase test) {
    if (test.getClass().getName().startsWith(TestSuite.class.getName())) {
      super.run(test);
    }
    else {
      String testKey = keyOf(test);
      if (!myPeformedTests.containsKey(testKey)) {
        super.run(test);
        myPeformedTests.put(testKey, test);
      }
    }
  }



  private static String keyOf(TestCase test) {
    return test.getClass().getName() + NOT_ALLOWED_IN_ID + test.getName() + NOT_ALLOWED_IN_ID + test.toString();
  }
}
