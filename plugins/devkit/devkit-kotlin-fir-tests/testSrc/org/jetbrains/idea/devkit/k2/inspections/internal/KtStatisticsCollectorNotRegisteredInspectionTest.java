// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.StatisticsCollectorNotRegisteredInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;

@TestDataPath("$CONTENT_ROOT/testData/inspections/statisticsCollectorNotRegistered")
public class KtStatisticsCollectorNotRegisteredInspectionTest extends StatisticsCollectorNotRegisteredInspectionTestBase implements
                                                                                                                         ExpectedPluginModeProvider {

  @Override
  public @NotNull KotlinPluginMode getPluginMode() {
    return KotlinPluginMode.K2;
  }

  @Override
  protected void setUp() throws Exception {
    ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this, getTestRootDisposable(), () -> super.setUp());
  }

  @Override
  protected String getSourceFileExtension() {
    return "kt";
  }

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/statisticsCollectorNotRegistered";
  }
}
