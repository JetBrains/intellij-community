// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@SuppressWarnings("NewClassNamingConvention")
@TestDataPath("$CONTENT_ROOT/testData/navigation/extensionDeclaration")
public class ExtensionPluginDescriptorDeclarationRelatedItemLineMarkerProviderTest
  extends ExtensionPluginDescriptorDeclarationRelatedItemLineMarkerProviderTestBase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/extensionDeclaration";
  }

  public void testInvalidExtension() {
    doTestInvalidExtension("MyInvalidExtension.java");
  }

  public void testExtension() {
    doTestExtension("MyExtension.java", "<myEp implementation=\"MyExtension\"/>");
  }

  public void testNestedClassExtension() {
    doTestExtension("MyNestedClassExtension.java", "<myEp implementation=\"MyNestedClassExtension$Nested\"/>");
  }
}
