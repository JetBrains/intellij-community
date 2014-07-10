/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy;

import com.intellij.TestAll;
import com.intellij.TestCaseLoader;
import com.intellij.openapi.externalSystem.test.ExternalSystemTestCase;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerTest;
import org.jetbrains.plugins.groovy.compiler.GroovyDebuggerTest;
import org.jetbrains.plugins.groovy.lang.GroovyStressPerformanceTest;

/**
 * @author Max Medvedev
 */
public class FastGroovyTestSuite {

  public static Test suite() throws Throwable {
    TestCaseLoader loader = new TestCaseLoader("", true);
    TestAll.fillTestCases(loader, "org.jetbrains.plugins.groovy", TestAll.getClassRoots());

    TestSuite suite = new TestSuite();
    for (Class aClass : loader.getClasses()) {
      if (!isSlow(aClass)) {
        suite.addTestSuite(aClass);
      }

    }
    return suite;
  }

  private static boolean isSlow(Class aClass) {
    return aClass.equals(GroovyDebuggerTest.class) ||
           aClass.equals(GroovyStressPerformanceTest.class) ||
           aClass.getName().startsWith(GroovyCompilerTest.class.getName()) ||
           ExternalSystemTestCase.class.isAssignableFrom(aClass);
  }
}
