/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.TestAll;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;

import javax.swing.*;
import java.util.List;

/**
 * This is should be first test in all tests so we can measure how long tests are starting up.
 *
 * @author max
 */
@SuppressWarnings({"JUnitTestClassNamingConvention", "UseOfSystemOutOrSystemErr"})
public class _FirstInSuiteTest extends TestCase {
  public static long suiteStarted = 0L;
  public static boolean nothingIsCalled = false;

  public void testReportClassLoadingProblems() {
    List<Throwable> problems = TestAll.getLoadingClassProblems();
    if (problems.isEmpty()) return;

    StringBuilder builder = new StringBuilder("The following test classes were not loaded:\n");
    for (Throwable each : problems) {
      builder.append(each.toString()).append("\n");
      each.printStackTrace(System.out);
    }

    throw new AssertionError(builder.toString());
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  public void testNothing() throws Exception {
    if (nothingIsCalled) return;

    nothingIsCalled = true;
    suiteStarted = System.nanoTime();

    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        System.out.println("EDT is " + Thread.currentThread());
      }
    });
    // in tests EDT inexplicably shuts down sometimes during the first access,
    // which leads to nasty problems in ApplicationImpl which assumes there is only one EDT.
    // so we try to forcibly terminate EDT here to urge JVM to re-spawn new shiny permanent EDT-1
    UsefulTestCase.replaceIdeEventQueueSafely();
    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        System.out.println("EDT is " + Thread.currentThread());
      }
    });
  }

  // performance tests
  public void testNothingPerformance() throws Exception {
    testNothing();
  }
}
