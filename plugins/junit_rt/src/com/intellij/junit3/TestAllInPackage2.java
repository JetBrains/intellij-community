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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TestAllInPackage2 extends TestSuite {
  public TestAllInPackage2(JUnit3IdeaTestRunner runner, final String name, String[] classMethodNames) {
    super(name);
    int testClassCount = 0;
    final Set allNames = new HashSet(Arrays.asList(classMethodNames));
    for (int i = 0; i < classMethodNames.length; i++) {
      String classMethodName = classMethodNames[i];
      Test suite = TestRunnerUtil.createClassOrMethodSuite(runner, classMethodName);
      if (suite != null) {
        skipSuiteComponents(allNames, suite);
      }
    }
    for (int i = 0; i < classMethodNames.length; i++) {
      String classMethodName = classMethodNames[i];
      Test suite = TestRunnerUtil.createClassOrMethodSuite(runner, classMethodName);
      if (suite != null) {
        boolean skip;
        if (suite instanceof TestSuite) {
          skip = !allNames.contains(((TestSuite)suite).getName());
        } else if (suite instanceof TestRunnerUtil.SuiteMethodWrapper) {
          skip = !allNames.contains(((TestRunnerUtil.SuiteMethodWrapper)suite).getClassName());
        } else {
          skip = false;
        }

        if (!skip) {
          if (suite instanceof TestSuite && ((TestSuite)suite).getName() == null) {
            attachSuiteInfo(suite, classMethodName);
          }
          addTest(suite);
          testClassCount++;
        }
      }
    }
    String message = TestRunnerUtil.testsFoundInPackageMesage(testClassCount, name);
    System.out.println(message);
  }

  private static void skipSuiteComponents(Set allNames, Test suite) {
    if (suite instanceof TestRunnerUtil.SuiteMethodWrapper) {
      final Test test = ((TestRunnerUtil.SuiteMethodWrapper)suite).getSuite();
      final String currentSuiteName =  ((TestRunnerUtil.SuiteMethodWrapper)suite).getClassName();
      skipSubtests(allNames, test, currentSuiteName);
    }
  }

  private static void skipSubtests(Set allNames, Test test, String currentSuiteName) {
    if (test instanceof TestSuite) {
      for (int idx = 0; idx < ((TestSuite)test).testCount(); idx++) {
        Test childTest = ((TestSuite)test).testAt(idx);
        final String testName = childTest.toString();
        if (!currentSuiteName.equals(testName)) {
          allNames.remove(testName);
        }
        skipSubtests(allNames, childTest, currentSuiteName);
      }
    }
  }

  private static void attachSuiteInfo(Test test, String name) {
    if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;
      if (testSuite.getName() == null)
        testSuite.setName(name);
    }
  }
}
