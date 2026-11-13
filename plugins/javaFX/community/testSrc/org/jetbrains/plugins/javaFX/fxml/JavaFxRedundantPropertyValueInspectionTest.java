// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxRedundantPropertyValueInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;

@TestApplication
public class JavaFxRedundantPropertyValueInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx =
    javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/redundantValue/",
                  () -> List.of(new JavaFxRedundantPropertyValueInspection()), tagName -> getHint(tagName));

  @Test
  public void testModifiedAttribute() {
    javaFx.get().checkQuickFixNotAvailable("alignment");
  }

  @Test
  public void testImmediateAttribute() {
    javaFx.get().doLaunchQuickfixTest("alignment");
  }

  @Test
  public void testInheritedAttribute() {
    javaFx.get().doLaunchQuickfixTest("maxHeight");
  }

  @Test
  public void testModifiedTag() {
    javaFx.get().checkQuickFixNotAvailable("alignment");
  }

  @Test
  public void testImmediateTag() {
    javaFx.get().doLaunchQuickfixTest("alignment");
  }

  @Test
  public void testInheritedTag() {
    javaFx.get().doLaunchQuickfixTest("maxHeight");
  }

  @Test
  public void testAttributeHighlighting() {
    doTestHighlighting();
  }

  @Test
  public void testTagHighlighting() {
    doTestHighlighting();
  }

  private void doTestHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml");
    myFixture.checkHighlighting();
  }

  private String getHint(String tagName) {
    if (javaFx.get().getTestName(false).endsWith("Attribute")) {
      return "Remove attribute " + tagName;
    }
    return "Remove tag " + tagName;
  }
}
