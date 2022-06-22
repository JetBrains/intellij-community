// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import com.intellij.lang.Language;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestApplicationManagerKt;
import com.intellij.tests.DynamicExtensionPointsTester;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    Assume.assumeTrue("intellij.test.all.dynamic.extension.points is off, no dynamic extensions to test", !DynamicExtensionPointsTester.EXTENSION_POINTS_WHITE_LIST.isEmpty() || testDynamicExtensions);
    DynamicExtensionPointsTester.checkDynamicExtensionPoints(_LastInSuiteTest::getTestName);
  }

  public void testProjectLeak() {
    if (Boolean.getBoolean("idea.test.guimode")) {
      Application application = ApplicationManager.getApplication();
      application.invokeAndWait(() -> {
        UIUtil.dispatchAllInvocationEvents();
        application.exit(true, true, false);
      });
      ShutDownTracker.getInstance().waitFor(100, TimeUnit.SECONDS);
      return;
    }

    TestApplicationManagerKt.disposeApplicationAndCheckForLeaks();
  }

  // should be run as late as possible to give Languages chance to instantiate as many of them as possible
  public void testLanguagesHaveDifferentDisplayNames() throws ClassNotFoundException {
    Collection<Language> languages = Language.getRegisteredLanguages();
    Map<String, Language> displayNames = new HashMap<>();
    for (Language language : languages) {
      Language prev = displayNames.put(language.getDisplayName(), language);
      if (prev != null) {
        fail(prev + " ("+prev.getClass()+") and " + language +" ("+language.getClass()+") both have identical display name: "+language.getDisplayName());
      }
    }
  }

  public void testStatistics() {
    long started = _FirstInSuiteTest.getSuiteStartTime();
    if (started != 0) {
      long testSuiteDuration = System.nanoTime() - started;
      System.out.printf("##teamcity[buildStatisticValue key='ideaTests.totalTimeMs' value='%d']%n", testSuiteDuration / 1000000);
    }
    LightPlatformTestCase.reportTestExecutionStatistics();
  }

  public void testFilenameIndexConsistency() {
    FSRecords.checkFilenameIndexConsistency();
  }
}
