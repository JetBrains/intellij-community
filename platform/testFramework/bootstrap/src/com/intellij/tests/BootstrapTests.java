/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author max
 */
public class BootstrapTests {
  static {
    ExternalClasspathClassLoader.install();
  }

  public static Test suite() throws Throwable {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    String[] classes = System.getProperty("bootstrap.testcases").split(",");
    TestSuite suite = new TestSuite();

    for (String s : classes) {
      final Class<?> aClass = Class.forName(s, true, cl);
      if (TestCase.class.isAssignableFrom(aClass)) {
        @SuppressWarnings("unchecked") final Class<? extends TestCase> testClass = (Class<? extends TestCase>)aClass;
        suite.addTestSuite(testClass);
      }
      else {
        suite.addTest((Test)aClass.getMethod("suite").invoke(null));
      }
    }
    return suite;
  }
}
