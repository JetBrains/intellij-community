// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.testFramework.*;
import com.intellij.util.Functions;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class JUnit5TestSessionListener implements LauncherSessionListener {
  boolean includeFirstLast = !"true".equals(System.getProperty("intellij.build.test.ignoreFirstAndLastTests")) && 
                             UsefulTestCase.IS_UNDER_TEAMCITY;
  private long suiteStarted = 0;

  @ReviseWhenPortedToJDK("13")
  @Override
  public void launcherSessionOpened(LauncherSession session) {
    session.getLauncher().registerTestExecutionListeners(new TestExecutionListener() {
      @Override
      public void testPlanExecutionStarted(TestPlan testPlan) {
        if (suiteStarted == 0) {
          if (!includeFirstLast) return;
          suiteStarted = System.nanoTime();
          Logger.setFactory(TestLoggerFactory.class);
          IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
          String tempDirectory = FileUtilRt.getTempDirectory();
          String[] list = new File(tempDirectory).list();
          assert list != null;
          System.out.println("FileUtil.getTempDirectory() = " + tempDirectory + " (" + list.length + " files)");

          System.out.println(Timings.getStatistics());

          Assertions.assertAll(() -> assertEncoding("file.encoding"),
                               () -> assertEncoding("sun.jnu.encoding"),

                               () -> Assertions.assertTrue(
                                 IoTestUtil.isSymLinkCreationSupported,
                                 String.format("Symlink creation not supported for %s on %s (%s)", SystemProperties.getUserName(),
                                               SystemInfo.OS_NAME,
                                               SystemInfo.OS_VERSION)),
                               () ->
                                 Assertions.assertEquals(
                                   "false", System.getProperty("sun.io.useCanonCaches", Runtime.version().feature() >= 13 ? "false" : ""),
                                   "The `sun.io.useCanonCaches` makes `File#getCanonical*` methods unreliable and should be set to `false`"));
        }
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
  public void launcherSessionClosed(LauncherSession session) {
    if (!includeFirstLast || suiteStarted == 0) return;

    try {
      Assertions.assertAll(() -> {
                             boolean testDynamicExtensions = SystemProperties.getBooleanProperty("intellij.test.all.dynamic.extension.points", false);
                             if (testDynamicExtensions) {
                               DynamicExtensionPointsTester.checkDynamicExtensionPoints(Functions.id());
                             }
                           },
                           () -> {
                             if (Boolean.getBoolean("idea.test.guimode")) {
                               Application application = ApplicationManager.getApplication();
                               application.invokeAndWait(() -> {
                                 UIUtil.dispatchAllInvocationEvents();
                                 application.exit(true, true, false);
                               });
                               ShutDownTracker.getInstance().waitFor(100, TimeUnit.SECONDS);
                             }
                             else {
                               TestApplicationManagerKt.disposeApplicationAndCheckForLeaks();
                             }
                           },
                           () -> {
                             Collection<Language> languages = Language.getRegisteredLanguages();
                             Map<String, Language> displayNames = new HashMap<>();
                             for (Language language : languages) {
                               System.out.println(language);
                               Language prev = displayNames.put(language.getDisplayName(), language);
                               if (prev != null) {
                                 Assertions.fail(prev +
                                                 " (" +
                                                 prev.getClass() +
                                                 ") and " +
                                                 language +
                                                 " (" +
                                                 language.getClass() +
                                                 ") both have identical display name: " +
                                                 language.getDisplayName());
                               }
                             }
                           });
    }
    finally {
      if (suiteStarted != 0) {
        long testSuiteDuration = System.nanoTime() - suiteStarted;
        System.out.printf("##teamcity[buildStatisticValue key='ideaTests.totalTimeMs' value='%d']%n", testSuiteDuration / 1000000);
      }
      LightPlatformTestCase.reportTestExecutionStatistics();
    }
  }
}
