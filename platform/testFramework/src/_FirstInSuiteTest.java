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

import com.intellij.TestAll;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.testFramework.TestRunnerUtil;
import com.intellij.testFramework.Timings;
import junit.framework.TestCase;

import javax.swing.*;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * This is should be first test in all tests so we can measure how long tests are starting up.
 *
 * @author max
 */
@SuppressWarnings({"JUnitTestClassNamingConvention", "UseOfSystemOutOrSystemErr"})
public class _FirstInSuiteTest extends TestCase {
  private static long suiteStarted;
  private static boolean nothingIsCalled;

  public static long getSuiteStartTime() {
    return suiteStarted;
  }

  public void testReportClassLoadingProblems() {
    List<Throwable> problems = TestAll.getLoadingClassProblems();
    if (problems.isEmpty()) return;

    StringBuilder builder = new StringBuilder("The following test classes were not loaded:\n");
    for (Throwable each : problems) {
      builder.append(each).append("\n");
      each.printStackTrace(System.out);
    }

    throw new AssertionError(builder.toString());
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  public void testNothing() throws Exception {
    if (nothingIsCalled) return;

    nothingIsCalled = true;

    // some tests do not initialize Application but want to use parallel streams
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool();

    suiteStarted = System.nanoTime();

    SwingUtilities.invokeAndWait(() -> System.out.println("EDT is " + Thread.currentThread()));
    // in tests EDT inexplicably shuts down sometimes during the first access,
    // which leads to nasty problems in ApplicationImpl which assumes there is only one EDT.
    // so we try to forcibly terminate EDT here to urge JVM to re-spawn new shiny permanent EDT-1
    TestRunnerUtil.replaceIdeEventQueueSafely();
    SwingUtilities.invokeAndWait(() -> System.out.println("EDT is " + Thread.currentThread()));

    // force platform JNA load
    Class.forName("com.sun.jna.Native");

    String tempDirectory = FileUtilRt.getTempDirectory();
    String[] list = new File(tempDirectory).list();
    assert list != null;
    System.out.println("FileUtil.getTempDirectory() = " + tempDirectory + " (" + list.length + " files)");

    Preferences.userRoot(); // starts (anonymous!) timer deep in JDK bowels. helps against thread leaks
  }

  // performance tests
  public void testNothingPerformance() throws Exception {
    Timings.getStatistics();
    testNothing();
  }

  // agents where this test is failing should be disabled and configured properly
  public void testFileEncoding() {
    assertEncoding("file.encoding");
    assertEncoding("sun.jnu.encoding");
  }

  private static void assertEncoding(String property) {
    String encoding = System.getProperty(property);
    System.out.println("** " + property + "=" + encoding);
    assertNotNull(encoding);
    assertFalse(Charset.forName(encoding).aliases().contains("default"));
  }
}