// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.lang.LanguageTestUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.testFramework.GlobalState;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.testFramework.Timings;
import com.intellij.testFramework.UITestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.Functions;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;

/**
 * This class is a replacement for {@linkplain _FirstInSuiteTest} and {@linkplain _LastInSuiteTest} when running JUnit5 tests
 * using the JUnit Jupiter test engine, or JUnit 3/4 tests using the JUnit Vintage test engine.
 *
 * @see _FirstInSuiteTest
 * @see _LastInSuiteTest
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "JavadocReference"})
public class JUnit5TestSessionListener implements LauncherSessionListener {
  private final boolean includeFirstLast = !"true".equals(System.getProperty("intellij.build.test.ignoreFirstAndLastTests")) &&
                                           UsefulTestCase.IS_UNDER_TEAMCITY;

  private final List<Throwable> caughtExceptions = new ArrayList<>();

  @ReviseWhenPortedToJDK("13")
  @Override
  public void launcherSessionOpened(LauncherSession session) {
    if (!includeFirstLast) return;
    session.getLauncher().registerTestExecutionListeners(new FirstAndLastInSuiteTestExecutionListener(caughtExceptions));
  }

  @Override
  public void launcherSessionClosed(LauncherSession session) {
    if (!caughtExceptions.isEmpty()) {
      RuntimeException e = new RuntimeException("Caught exception in _FirstInSuiteTest/_LastInSuiteTest", caughtExceptions.getFirst());
      caughtExceptions.stream().skip(1).forEach(e::addSuppressed);
      throw e;  // rethrow to exit abnormally
    }
  }

  private static class FirstAndLastInSuiteTestExecutionListener implements TestExecutionListener {
    private static final String BOOTSTRAP_TESTS_SUITE_NAME = "com.intellij.tests.BootstrapTests";  // reuse existing mutes on TeamCity
    private static final String VINTAGE_UNIQUE_ID = UniqueId.forEngine("junit-vintage").toString();

    private final List<Throwable> caughtExceptions;
    private long suiteStarted = 0;

    private FirstAndLastInSuiteTestExecutionListener(List<Throwable> caughtExceptions) {
      this.caughtExceptions = caughtExceptions;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
      // same as _FirstInSuiteTest
      final String _FirstInSuiteTestPrefix = "_FirstInSuiteTest.";

      // TODO: use the same logic for tests, remove junit34Test
      final boolean junit34Test = ContainerUtil.exists(testPlan.getRoots(), root -> root.getUniqueId().equals(VINTAGE_UNIQUE_ID));
      final BiConsumer<String, ThrowableRunnable<?>> doTest = (junit34Test ? this::catchExceptionAndReportAsBootstrapTestsSuite : this::catchException);

      // no testReportClassLoadingProblems

      // testNothing
      doTest.accept(_FirstInSuiteTestPrefix + "testNothing", () -> {
        suiteStarted = System.nanoTime();

        if (junit34Test) {
          IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
          SwingUtilities.invokeAndWait(() -> System.out.println("EDT is " + Thread.currentThread()));
          // in tests EDT inexplicably shuts down sometimes during the first access,
          // which leads to nasty problems in ApplicationImpl which assumes there is only one EDT.
          // so we try to forcibly terminate EDT here to urge JVM to re-spawn new shiny permanent EDT-1
          UITestUtil.replaceIdeEventQueueSafely();
          SwingUtilities.invokeAndWait(() -> System.out.println("EDT is " + Thread.currentThread()));

          // force platform JNA load
          Class.forName("com.sun.jna.Native");
        }

        String tempDirectory = FileUtilRt.getTempDirectory();
        String[] list = new File(tempDirectory).list();
        assert list != null;
        System.out.println("FileUtil.getTempDirectory() = " + tempDirectory + " (" + list.length + " files)");

        if (junit34Test) {
          Preferences.userRoot(); // starts (anonymous!) timer deep in JDK bowels. helps against thread leaks
        }
      });

      // testNothingPerformance
      System.out.println(Timings.getStatistics());

      // testFileEncoding
      doTest.accept(_FirstInSuiteTestPrefix + "testFileEncoding", () -> {
        assertEncoding("file.encoding");
        assertEncoding("sun.jnu.encoding");
      });

      // testSymlinkAbility
      doTest.accept(_FirstInSuiteTestPrefix + "testSymlinkAbility", () -> {
        Assertions.assertTrue(
          IoTestUtil.isSymLinkCreationSupported,
          String.format("Symlink creation not supported for %s on %s (%s)",
                        SystemProperties.getUserName(), SystemInfo.OS_NAME, SystemInfo.OS_VERSION));
        Assertions.assertEquals(
          "false", System.getProperty("sun.io.useCanonCaches", Runtime.version().feature() >= 13 ? "false" : ""),
          "The `sun.io.useCanonCaches` makes `File#getCanonical*` methods unreliable and should be set to `false`");
      });

      // testGlobalState
      doTest.accept(_FirstInSuiteTestPrefix + "testGlobalState", () -> {
        if (junit34Test) {
          GlobalState.checkSystemStreams(); // Rather initialize than check.
        }
      });
    }

    private static void assertEncoding(@NotNull String property) {
      String encoding = System.getProperty(property);
      System.out.println("** " + property + "=" + encoding);
      Assertions.assertNotNull(encoding, "The property '" + property + "' is 'null'. Please check build configuration settings.");
      Assertions.assertFalse(
        Charset.forName(encoding).aliases().contains("default"),
        "The property '" + property + "' is set to a default value. Please make sure the build agent has sane locale settings.");
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
      // same as _LastInSuiteTest
      final String _LastInSuiteTestPrefix = "_LastInSuiteTest.";
      String buildConfName = System.getProperty("teamcity.buildConfName", "");
      if (!buildConfName.isEmpty()) buildConfName = "[" + buildConfName + "]";

      // TODO: use the same logic for tests, remove junit34Test
      final boolean junit34Test = ContainerUtil.exists(testPlan.getRoots(), root -> root.getUniqueId().equals(VINTAGE_UNIQUE_ID));
      final BiConsumer<String, ThrowableRunnable<?>> doTest = (junit34Test ? this::catchExceptionAndReportAsBootstrapTestsSuite : this::catchException);

      // setUp
      if (junit34Test) {
        Disposer.setDebugMode(true);
      }

      // testDynamicExtensions
      doTest.accept(_LastInSuiteTestPrefix + "testDynamicExtensions" + buildConfName, () -> {
        boolean testDynamicExtensions = SystemProperties.getBooleanProperty("intellij.test.all.dynamic.extension.points", false);
        if (!DynamicExtensionPointsTester.EXTENSION_POINTS_WHITE_LIST.isEmpty() || testDynamicExtensions) {
          if (testDynamicExtensions) {
            DynamicExtensionPointsTester.checkDynamicExtensionPoints(Functions.id());
          }
        }
        else {
          // intellij.test.all.dynamic.extension.points is off, no dynamic extensions to test
        }
      });

      // testProjectLeak
      doTest.accept(_LastInSuiteTestPrefix + "testProjectLeak" + buildConfName, () -> {
        TestApplicationManager.testProjectLeak();
      });

      // testLanguagesHaveDifferentDisplayNames
      doTest.accept(_LastInSuiteTestPrefix + "testLanguagesHaveDifferentDisplayNames" + buildConfName, () -> {
        LanguageTestUtil.assertAllLanguagesHaveDifferentDisplayNames();
      });

      // testFilenameIndexConsistency
      doTest.accept(_LastInSuiteTestPrefix + "testFilenameIndexConsistency" + buildConfName, () -> {
        if (junit34Test) {
          FSRecords.checkFilenameIndexConsistency();
        }
      });

      // testGlobalState
      doTest.accept(_LastInSuiteTestPrefix + "testGlobalState" + buildConfName, () -> {
        if (junit34Test) {
          GlobalState.checkSystemStreams();
        }
      });

      // testStatistics
      doTest.accept(_LastInSuiteTestPrefix + "testStatistics" + buildConfName, () -> {
        if (suiteStarted != 0) {
          long testSuiteDuration = System.nanoTime() - suiteStarted;
          System.out.printf("##teamcity[buildStatisticValue key='ideaTests.totalTimeMs' value='%d']%n", testSuiteDuration / 1_000_000);
        }
        LightPlatformTestCase.reportTestExecutionStatistics();
      });
    }

    private void catchException(String testName, ThrowableRunnable<?> test) {
      try {
        test.run();
      }
      catch (Throwable e) {
        caughtExceptions.add(e);
      }
    }

    private void catchExceptionAndReportAsBootstrapTestsSuite(String testName, ThrowableRunnable<?> test) {
      long started = System.nanoTime();

      String escapedTestName = teamcityStdEscaper2(testName);
      System.out.printf("##teamcity[testSuiteStarted name='%s']%n", BOOTSTRAP_TESTS_SUITE_NAME);
      System.out.printf("##teamcity[testStarted name='%s' captureStandardOutput='true']%n", escapedTestName);
      try {
        test.run();
      }
      catch (Throwable e) {
        caughtExceptions.add(e);
        StringWriter stacktrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stacktrace));
        System.out.printf("##teamcity[testFailed name='%s' message='%s' details='%s']%n", escapedTestName, teamcityStdEscaper2(e.toString()), teamcityStdEscaper2(stacktrace.toString()));
      }
      finally {
        long duration = System.nanoTime() - started;
        System.out.printf("##teamcity[testFinished name='%s' duration='%d']%n", escapedTestName, duration / 1_000_000);
        System.out.printf("##teamcity[testSuiteFinished name='%s']%n", BOOTSTRAP_TESTS_SUITE_NAME);
      }
    }

    // https://www.jetbrains.com/help/teamcity/service-messages.html#Escaped+Values
    private static String teamcityStdEscaper2(String s) {
      StringBuilder result = new StringBuilder(s.length());
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        switch (c) {
          case '\'': result.append("|'"); break;
          case '\n': result.append("|n"); break;
          case '\r': result.append("|r"); break;
          case '|':  result.append("||"); break;
          case '[':  result.append("|["); break;
          case ']':  result.append("|]"); break;
          default:   result.append(c < 128 ? c : String.format("|0x%04x", (int)c));
        }
      }
      return result.toString();
    }
  }
}
