// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.lang.LanguageTestUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.testFramework.GlobalState;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestApplicationManager;
import junit.framework.TestCase;
import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

/**
 * This must be the last test.
 *
 * @author max
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SuppressWarnings({"JUnitTestClassNamingConvention", "UseOfSystemOutOrSystemErr", "TestInProductSource"})
@RunWith(JUnit38AssumeSupportRunner.class)
public class _LastInSuiteTest extends TestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Disposer.setDebugMode(true);
  }

  @Override
  public String getName() {
    return getTestName(super.getName());
  }

  private static String getTestName(String name) {
    String buildConf = System.getProperty("teamcity.buildConfName");
    return buildConf == null ? name : name + "[" + buildConf + "]";
  }

  public void testProjectLeak() {
    TestApplicationManager.testProjectLeak();
  }

  // should be run as late as possible to give the Languages the chance to instantiate as many of them as possible
  public void testLanguagesHaveDifferentDisplayNames() {
    LanguageTestUtil.assertAllLanguagesHaveDifferentDisplayNames();
  }

  public void testStatistics() {
    long started = _FirstInSuiteTest.getSuiteStartTime();
    if (started != 0) {
      long testSuiteDuration = System.nanoTime() - started;
      System.out.printf("##teamcity[buildStatisticValue key='ideaTests.totalTimeMs' value='%d']%n", testSuiteDuration / 1_000_000);
    }
    LightPlatformTestCase.reportTestExecutionStatistics();
  }

  public void testFilenameIndexConsistency() {
    FSRecords.checkFilenameIndexConsistency();
  }

  public void testGlobalState() {
    GlobalState.checkSystemStreams();
  }
}
