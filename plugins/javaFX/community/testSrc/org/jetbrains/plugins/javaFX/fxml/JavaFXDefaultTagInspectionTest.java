// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxDefaultTagInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;

@TestApplication
public class JavaFXDefaultTagInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx =
    javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/defaultTag/",
                  () -> List.of(new JavaFxDefaultTagInspection()), tagName -> getHint(tagName));

  @Test
  public void testChildren() {
    javaFx.get().doLaunchQuickfixTest("children");
  }

  @Test
  public void testEmptyChildren() {
    javaFx.get().doLaunchQuickfixTest("children");
  }

  @Test
  public void testFxCollectionsHighlighting() {
    doHighlightingTest();
  }

  @Test
  public void testEmptyListHighlighting() {
    doHighlightingTest();
  }

  @Test
  public void testEmptyCollapsedListHighlighting() {
    doHighlightingTest();
  }

  @Test
  public void testStylesheets() {
    javaFx.get().checkQuickFixNotAvailable("stylesheets");
  }

  private void doHighlightingTest() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml");
    myFixture.checkHighlighting();
  }

  private static String getHint(String tagName) {
    return "Unwrap '" + tagName + "'";
  }
}
