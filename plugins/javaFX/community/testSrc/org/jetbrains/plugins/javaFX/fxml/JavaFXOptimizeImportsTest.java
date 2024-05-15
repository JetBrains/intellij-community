// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;

public class JavaFXOptimizeImportsTest extends AbstractJavaFXTestCase {
  public void testCollapseOnDemand() {
    doTest();
  }

  public void testRemoveUnused() {
    doTest();
  }

  public void testDblImports() {
    doTest();
  }

  public void testStaticPropertiesAttrAndCustomComponents() {
    myFixture.addClass("""
                         import javafx.scene.layout.GridPane;
                         public class MyGridPane extends GridPane {}
                         """);
    doTest();
  }

  public void testStaticPropertiesTagAndCustomComponents() {
    myFixture.addClass("""
                         import javafx.scene.layout.GridPane;
                         public class MyGridPane extends GridPane {}
                         """);
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".fxml");
    new OptimizeImportsProcessor(getProject(), myFixture.getFile()).run();
    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/optimizeImports/";
  }
}
