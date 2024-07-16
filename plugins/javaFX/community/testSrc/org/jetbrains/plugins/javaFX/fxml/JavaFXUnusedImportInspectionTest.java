// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxUnusedImportsInspection;

public class JavaFXUnusedImportInspectionTest extends AbstractJavaFXTestCase {

  @Override
  protected void enableInspections() {
    myFixture.enableInspections(new JavaFxUnusedImportsInspection());
  }


  public void testUnusedUnrelatedImports() {
    myFixture.configureByFile(getTestName(true) + ".fxml");
    myFixture.checkHighlighting();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/unusedImport/";
  }
}
