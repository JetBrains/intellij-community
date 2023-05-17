// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.lang.Language;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.testFramework.GlobalState;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.tests.DynamicExtensionPointsTester;
import com.intellij.util.SystemProperties;
import junit.framework.TestCase;
import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * This must be the last test.
 *
 * @author max
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SuppressWarnings({"JUnitTestClassNamingConvention", "UseOfSystemOutOrSystemErr"})
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

  public void testDynamicExtensions() {
    boolean testDynamicExtensions = SystemProperties.getBooleanProperty("intellij.test.all.dynamic.extension.points", false);
    Assume.assumeTrue("intellij.test.all.dynamic.extension.points is off, no dynamic extensions to test",
                      !DynamicExtensionPointsTester.EXTENSION_POINTS_WHITE_LIST.isEmpty() || testDynamicExtensions);
    DynamicExtensionPointsTester.checkDynamicExtensionPoints(_LastInSuiteTest::getTestName);
  }

  public void testProjectLeak() {
    TestApplicationManager.testProjectLeak();
  }

  // should be run as late as possible to give the Languages the chance to instantiate as many of them as possible
  public void testLanguagesHaveDifferentDisplayNames() {
    Collection<Language> languages = Language.getRegisteredLanguages();
    Map<String, Language> displayNames = new HashMap<>();
    for (Language language : languages) {
      Language prev = displayNames.put(language.getDisplayName(), language);
      if (prev != null) {
        fail("The languages '%s' (%s) and '%s' (%s) have the same display name '%s'"
               .formatted(prev, prev.getClass().getName(), language, language.getClass().getName(), language.getDisplayName()));
      }
    }
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
