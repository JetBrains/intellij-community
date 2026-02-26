// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.internal.UseDPIAwareEmptyBorderInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useDPIAwareEmptyBorder")
public class KtUseDPIAwareEmptyBorderInspectionTest extends UseDPIAwareEmptyBorderInspectionTestBase implements ExpectedPluginModeProvider {

  @Override
  protected void setUp() throws Exception {
    ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this, getTestRootDisposable(), () -> super.setUp());
  }

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/useDPIAwareEmptyBorder";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "kt";
  }

  public void testUseJBUIBordersEmptyThatCanBeSimplified() {
    doTest();
  }

  public void testUseSwingEmptyBorder() {
    doTest();
  }

  @Override
  public @NotNull KotlinPluginMode getPluginMode() {
    return KotlinPluginMode.K2;
  }
}
