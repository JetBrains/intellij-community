// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.navigation;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.idea.devkit.navigation.DescriptionTypeRelatedItemLineMarkerProviderTestBase;

@TestDataPath("$CONTENT_ROOT/testData/navigation/descriptionType")
public class KtDescriptionTypeRelatedItemLineMarkerProviderTest extends DescriptionTypeRelatedItemLineMarkerProviderTestBase {
  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "navigation/descriptionType";
  }

  public void testKtInspectionDescription() {
    doTestInspectionDescription("MyKtWithDescriptionInspection.kt", "MyKtWithDescription.html");
  }
}
