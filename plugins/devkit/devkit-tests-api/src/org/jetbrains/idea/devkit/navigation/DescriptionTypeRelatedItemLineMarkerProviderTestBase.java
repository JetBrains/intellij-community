// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.icons.AllIcons;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

public abstract class DescriptionTypeRelatedItemLineMarkerProviderTestBase extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    String pathForClass = PathUtil.getJarPathForClass(LocalInspectionEP.class);
    moduleBuilder.addLibrary("lang-api", pathForClass);
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class);
    moduleBuilder.addLibrary("platform-api", platformApiJar);
  }

  protected void doTestInspectionDescription(@NotNull String inspectionFile, @NotNull String descriptionFile) {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions");
    GutterMark gutter = myFixture.findGutter(inspectionFile);
    DevKitGutterTargetsChecker.checkGutterTargets(gutter, "Description", AllIcons.FileTypes.Html, descriptionFile);
  }
}
