// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import junit.framework.TestCase;

import java.util.concurrent.TimeUnit;

/**
 * This must be the last test.
 *
 * @author max
 */
@SuppressWarnings({"JUnitTestClassNamingConvention", "UseOfSystemOutOrSystemErr"})
public class _LastInSuiteTest extends TestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Disposer.setDebugMode(true);
  }

  @Override
  public String getName() {
    String name = super.getName();
    String buildConf = System.getProperty("teamcity.buildConfName");
    return buildConf == null ? name : name + "[" + buildConf + "]";
  }

  public void testProjectLeak() {
    if (Boolean.getBoolean("idea.test.guimode")) {
      Application application = ApplicationManager.getApplication();
      TransactionGuard.getInstance().submitTransactionAndWait(() -> {
        IdeEventQueue.getInstance().flushQueue();
        application.exit(true, true, false);
      });
      ShutDownTracker.getInstance().waitFor(100, TimeUnit.SECONDS);
      return;
    }

    PlatformTestUtil.disposeApplicationAndCheckForProjectLeaks();

    try {
      Disposer.assertIsEmpty(true);
    }
    catch (AssertionError | Exception e) {
      PlatformTestUtil.captureMemorySnapshot();
      throw e;
    }
  }

  public void testStatistics() {
    long started = _FirstInSuiteTest.getSuiteStartTime();
    if (started != 0) {
      long testSuiteDuration = System.nanoTime() - started;
      System.out.println(String.format("##teamcity[buildStatisticValue key='ideaTests.totalTimeMs' value='%d']", testSuiteDuration / 1000000));
    }
    LightPlatformTestCase.reportTestExecutionStatistics();
  }

}