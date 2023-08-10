// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitIcons;

public abstract class DescriptionTypeRelatedItemLineMarkerProviderTestBase extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("platform-analysis", PathUtil.getJarPathForClass(LocalInspectionEP.class));
    moduleBuilder.addLibrary("platform-ide", PathUtil.getJarPathForClass(JBList.class));
  }

  protected void doTestInspectionDescription(@NotNull String inspectionFile, @NotNull String descriptionFile) {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions");
    GutterMark gutter = myFixture.findGutter(inspectionFile);
    DevKitGutterTargetsChecker.checkGutterTargets(gutter, "Description", DevKitIcons.Gutter.DescriptionFile, descriptionFile);
  }
}
