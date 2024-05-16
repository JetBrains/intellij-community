// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxDefaultTagInspection;

public class JavaFXDefaultTagInspectionTest extends AbstractJavaFXQuickFixTest {

  @Override
  protected void enableInspections() {
    myFixture.enableInspections(new JavaFxDefaultTagInspection());
  }


  public void testChildren() {
    doLaunchQuickfixTest("children");
  }

  public void testEmptyChildren() {
    doLaunchQuickfixTest("children");
  }

  public void testFxCollectionsHighlighting() {
    doHighlightingTest();
  }

  public void testEmptyListHighlighting() {
    doHighlightingTest();
  }

  public void testEmptyCollapsedListHighlighting() {
    doHighlightingTest();
  }

  public void testStylesheets() {
    checkQuickFixNotAvailable("stylesheets");
  }

  private void doHighlightingTest() {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    myFixture.checkHighlighting();
  }

  @Override
  protected String getHint(String tagName) {
    return "Unwrap '" + tagName + "'";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/defaultTag/";
  }
}
