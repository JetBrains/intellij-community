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
package com.intellij.junit3;

import junit.framework.Test;
import junit.framework.TestSuite;

public class TestAllInPackage2 extends TestSuite {
  public TestAllInPackage2(JUnit3IdeaTestRunner runner, final String name, String[] classMethodNames) {
    super(name);
    int testClassCount = 0;

    for (int i = 0; i < classMethodNames.length; i++) {
      String classMethodName = classMethodNames[i];
      Test suite = TestRunnerUtil.createClassOrMethodSuite(runner, classMethodName);
      if (suite != null) {
        if (suite instanceof TestSuite && ((TestSuite)suite).getName() == null) {
          attachSuiteInfo(suite, classMethodName);
        }
        addTest(suite);
        testClassCount++;
      }
    }
    String message = TestRunnerUtil.testsFoundInPackageMesage(testClassCount, name);
    System.out.println(message);
  }

  private static void attachSuiteInfo(Test test, String name) {
    if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;
      if (testSuite.getName() == null)
        testSuite.setName(name);
    }
  }
}
