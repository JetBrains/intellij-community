/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tests;

import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

/** Runs classes listed in the comma-delimited {@code bootstrap.testcase} system property. */
public class BootstrapTestcaseSuite extends Suite {

  public BootstrapTestcaseSuite(Class<?> suiteClass, RunnerBuilder builder) throws InitializationError {
    super(builder, suiteClass, getClasses(getClassLoader(), getClassNames()));
  }

  private static Class<?>[] getClasses(ClassLoader classLoader, String[] testNames) {
    Class<?>[] classArray = new Class<?>[testNames.length];
    for (int i = 0; i < testNames.length; i++) {
      try {
        classArray[i] = Class.forName(testNames[i], true, classLoader);
      } catch (ClassNotFoundException ex) {
        throw new IllegalStateException(ex);
      }
    }
    return classArray;
  }

  private static String[] getClassNames() {
    String testSpec = System.getProperty("bootstrap.testcase");
    if (testSpec == null) {
      throw new IllegalStateException("No tests specified via -Dbootstrap.testcase property");
    }
    return testSpec.split(",");
  }

  private static ClassLoader getClassLoader() {
    ExternalClasspathClassLoader.install();
    return Thread.currentThread().getContextClassLoader();
  }
}
