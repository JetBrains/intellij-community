// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.navigation;

import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.idea.devkit.navigation.ExtensionPointDeclarationRelatedItemLineMarkerProviderTestBase;

public class KtExtensionPointDeclarationRelatedItemLineMarkerProviderTest extends
                                                                          ExtensionPointDeclarationRelatedItemLineMarkerProviderTestBase {
  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "navigation/extensionPointDeclaration";
  }

  @Override
  protected String getExtension() {
    return ".kt";
  }

  public void testMyStringEP() {
    assertStringEP();
  }

  public void testMyStringEPViaConstant() {
    assertStringEP();
  }

  public void testMyStringEPConstructor() {
    assertStringEP();
  }

  public void testMyStringEPLanguageExtension() {
    assertStringEP();
  }

  public void testMyStringProjectEP() {
    assertStringEP();
  }
}
