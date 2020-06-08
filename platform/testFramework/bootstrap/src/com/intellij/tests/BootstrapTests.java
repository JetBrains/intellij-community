// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public final class BootstrapTests {
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
