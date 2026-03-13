// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.navigation;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.idea.devkit.navigation.DescriptionTypeRelatedItemLineMarkerProviderTestBase;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;

@TestDataPath("/navigation/descriptionType")
public class KtDescriptionTypeRelatedItemLineMarkerProviderTest extends DescriptionTypeRelatedItemLineMarkerProviderTestBase implements
                                                                                                                             ExpectedPluginModeProvider {
  @Override
  public @NotNull KotlinPluginMode getPluginMode() {
    return KotlinPluginMode.K2;
  }

  @Override
  protected void setUp() {
    ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this, getTestRootDisposable(), () -> super.setUp());
  }

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "navigation/descriptionType";
  }

  public void testKtInspectionDescription() {
    doTestInspectionDescription("MyKtWithDescriptionInspection.kt", "MyKtWithDescription.html");
  }
}
