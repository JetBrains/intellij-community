// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;

@TestApplication
public class JavaFXOptimizeImportsTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx =
    javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/optimizeImports/");

  @Test
  public void testCollapseOnDemand() {
    doTest();
  }

  @Test
  public void testRemoveUnused() {
    doTest();
  }

  @Test
  public void testDblImports() {
    doTest();
  }

  @Test
  public void testStaticPropertiesAttrAndCustomComponents() {
    javaFx.get().getFixture().addClass("""
                         import javafx.scene.layout.GridPane;
                         public class MyGridPane extends GridPane {}
                         """);
    doTest();
  }

  @Test
  public void testStaticPropertiesTagAndCustomComponents() {
    javaFx.get().getFixture().addClass("""
                         import javafx.scene.layout.GridPane;
                         public class MyGridPane extends GridPane {}
                         """);
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(true) + ".fxml");
    EdtTestUtil.runInEdtAndWait(() -> {
      new OptimizeImportsProcessor(myFixture.getProject(), myFixture.getFile()).run();
      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.fxml");
    });
  }
}
