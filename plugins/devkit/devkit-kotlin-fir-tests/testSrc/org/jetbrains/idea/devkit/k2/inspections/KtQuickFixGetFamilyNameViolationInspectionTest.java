// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.QuickFixGetFamilyNameViolationInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;

/**
 * @author Dmitry Batkovich
 */
@TestDataPath("$CONTENT_ROOT/testData/inspections/getFamilyNameViolation")
public class KtQuickFixGetFamilyNameViolationInspectionTest extends QuickFixGetFamilyNameViolationInspectionTestBase implements
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
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/getFamilyNameViolation";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "kt";
  }

  public void testNotViolatedByField() {
    doTest();
  }

  public void testNotViolatedByGetName() {
    doTest();
  }

  public void testNotViolatedByExternalParameter() {
    doTest();
  }

  public void testNotViolatedStaticField() {
    doTest();
  }

  public void testNotViolatedStaticMethod() {
    doTest();
  }

  public void testNotViolatedGetNameMethod() {
    doTest();
  }

  public void testViolationByPsiElementFieldUsage() {
    doTest();
  }

  public void testViolationByPsiElementFieldUsageInUsedMethod() {
    doTest();
  }

  public void testViolationByPsiElementFieldUsageInUsedParentClassMethod() {
    doTest();
  }
}
