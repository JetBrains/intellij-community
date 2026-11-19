// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.inspections.StatisticsCollectorNotRegisteredFixture;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.junit.jupiter.api.Test;

import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleProjectFixture;
import static org.jetbrains.idea.devkit.inspections.StatisticsCollectorNotRegisteredFixtureKt.statisticsCollectorNotRegisteredFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/statisticsCollectorNotRegistered")
@TestApplication
public class KtStatisticsCollectorNotRegisteredInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = pluginModuleProjectFixture();
  private final TestFixture<StatisticsCollectorNotRegisteredFixture> collectorNotRegistered =
    statisticsCollectorNotRegisteredFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/statisticsCollectorNotRegistered");

  @Test
  public void testRegisteredStatisticsCollector() {
    collectorNotRegistered.get().doTestRegisteredStatisticsCollector();
  }

  @Test
  public void testRegisteredNestedStatisticsCollector() {
    collectorNotRegistered.get().doTestRegisteredNestedStatisticsCollector();
  }

  @Test
  public void testUnregisteredCounterCollector() {
    collectorNotRegistered.get().doTestUnregisteredCounterCollector();
  }

  @Test
  public void testUnregisteredProjectCollector() {
    collectorNotRegistered.get().doTestUnregisteredProjectCollector();
  }

  @Test
  public void testUnregisteredNestedCountCollector() {
    collectorNotRegistered.get().doTestUnregisteredNestedCountCollector();
  }
}