// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.TestAll;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.testFramework.GlobalState;
import com.intellij.testFramework.Timings;
import com.intellij.testFramework.UITestUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

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

  static long getSuiteStartTime() {
    return suiteStarted;
  }

  public void testReportClassLoadingProblems() {
    List<Throwable> problems = TestAll.getLoadingClassProblems();
    if (problems.isEmpty()) return;

    StringBuilder builder = new StringBuilder("The following test classes were not loaded:\n");
    for (Throwable each : problems) {
      builder.append(ExceptionUtil.getThrowableText(each)).append("\n");
      each.printStackTrace(System.out);
    }

    throw new AssertionError(builder.toString());
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  public void testNothing() throws Exception {
    if (nothingIsCalled) return;

    nothingIsCalled = true;

    suiteStarted = System.nanoTime();
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
    SwingUtilities.invokeAndWait(() -> System.out.println("EDT is " + Thread.currentThread()));
    // in tests EDT inexplicably shuts down sometimes during the first access,
    // which leads to nasty problems in ApplicationImpl which assumes there is only one EDT.
    // so we try to forcibly terminate EDT here to urge JVM to re-spawn new shiny permanent EDT-1
    UITestUtil.replaceIdeEventQueueSafely();
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
    System.out.println(Timings.getStatistics());
    testNothing();
  }

  // agents where this test is failing should be disabled and configured properly
  public void testFileEncoding() {
    assertEncoding("file.encoding");
    assertEncoding("sun.jnu.encoding");
  }

  private static void assertEncoding(@NotNull String property) {
    String encoding = System.getProperty(property);
    System.out.println("** " + property + "=" + encoding);
    assertNotNull("The property '" + property + "' is 'null'. Please check build configuration settings.", encoding);
    assertFalse(
      "The property '" + property + "' is set to a default value. Please make sure the build agent has sane locale settings.",
      Charset.forName(encoding).aliases().contains("default"));
  }

  // agents where this test is failing should be disabled and configured properly
  @ReviseWhenPortedToJDK("13")
  public void testSymlinkAbility() {
    assertTrue(
      String.format("Symlink creation not supported for %s on %s (%s)", SystemProperties.getUserName(), SystemInfo.OS_NAME, SystemInfo.OS_VERSION),
      IoTestUtil.isSymLinkCreationSupported);
    assertEquals(
      "The `sun.io.useCanonCaches` makes `File#getCanonical*` methods unreliable and should be set to `false`",
      "false", System.getProperty("sun.io.useCanonCaches", Runtime.version().feature() >= 13 ? "false" : ""));
  }

  public void testGlobalState() {
    GlobalState.checkSystemStreams(); // Rather initialize than check.
  }
}
