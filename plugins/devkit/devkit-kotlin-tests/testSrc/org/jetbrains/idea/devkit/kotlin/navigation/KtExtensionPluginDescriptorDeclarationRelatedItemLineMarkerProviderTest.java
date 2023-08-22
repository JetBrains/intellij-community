// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.navigation;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.idea.devkit.navigation.ExtensionPluginDescriptorDeclarationRelatedItemLineMarkerProviderTestBase;

@SuppressWarnings("NewClassNamingConvention")
@TestDataPath("$CONTENT_ROOT/testData/navigation/extensionDeclaration")
public class KtExtensionPluginDescriptorDeclarationRelatedItemLineMarkerProviderTest
  extends ExtensionPluginDescriptorDeclarationRelatedItemLineMarkerProviderTestBase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "navigation/extensionDeclaration";
  }

  public void testInvalidKtExtension() {
    doTestInvalidExtension("MyInvalidKtExtension.kt");
  }

  public void testKtExtension() {
    doTestExtension("MyKtExtension.kt", "<myEp implementation=\"MyKtExtension\"/>");
  }
}
