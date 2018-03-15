// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.navigation.DevKitGutterTargetsChecker;

//TODO fix copy-paste
@TestDataPath("$CONTENT_ROOT/testData/navigation/descriptionType")
public class KtDescriptionTypeRelatedItemLineMarkerProviderTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/navigation/descriptionType";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    String pathForClass = PathUtil.getJarPathForClass(LocalInspectionEP.class);
    moduleBuilder.addLibrary("lang-api", pathForClass);
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class);
    moduleBuilder.addLibrary("platform-api", platformApiJar);
  }

  public void testKtInspectionDescription() {
    doTestInspectionDescription("MyKtWithDescriptionInspection.kt", "MyKtWithDescription.html");
  }

  private void doTestInspectionDescription(String inspectionFile, String descriptionFile) {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions");
    GutterMark gutter = myFixture.findGutter(inspectionFile);
    DevKitGutterTargetsChecker.checkGutterTargets(gutter, "Description", AllIcons.FileTypes.Html, descriptionFile);
  }
}
