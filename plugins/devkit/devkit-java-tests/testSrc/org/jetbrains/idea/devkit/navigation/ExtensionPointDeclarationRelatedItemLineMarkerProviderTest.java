// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/navigation/extensionPointDeclaration")
public class ExtensionPointDeclarationRelatedItemLineMarkerProviderTest
  extends ExtensionPointDeclarationRelatedItemLineMarkerProviderTestBase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/extensionPointDeclaration";
  }

  @Override
  protected String getExtension() {
    return ".java";
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

  public void testMyStringKeyedLazyInstanceEP() {
    assertStringEP();
  }

  public void testMyBeanClassStringEP() {
    assertSingleEPDeclaration("MyBeanClassStringEP.java", "com.intellij.myBeanClassStringEP");
  }
}