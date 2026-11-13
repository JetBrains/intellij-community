// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxEventHandlerInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaFxEventHandlerInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx =
    javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/eventHandler/",
                  () -> List.of(new JavaFxEventHandlerInspection()));

  @Test
  public void testHighlightExact() {
    doHighlightingTest();
  }

  @Test
  public void testHighlightNonVoid() {
    final JavaFxEventHandlerInspection inspection = new JavaFxEventHandlerInspection();
    inspection.myDetectNonVoidReturnType = true;
    javaFx.get().getFixture().enableInspections(inspection);
    doHighlightingTest();
  }

  @Test
  public void testHighlightAmbiguous() {
    doHighlightingTest();
  }

  @Test
  public void testHighlightGeneric() {
    doHighlightingTest();
  }

  @Test
  public void testHighlightRaw() {
    doHighlightingTest();
  }

  @Test
  public void testHighlightHalfRaw() {
    doHighlightingTest();
  }

  @Test
  public void testHighlightSpecific() {
    doHighlightingTest();
  }

  @Test
  public void testHighlightSuper() {
    doHighlightingTest();
  }

  @Test
  public void testHighlightWildcard() {
    doHighlightingTest();
  }

  @Test
  public void testQuickfixRaw() {
    doQuickfixTest("Create method 'onSort'");
  }

  @Test
  public void testQuickfixHalfRaw() {
    doQuickfixTest("Create method 'onSort'");
  }

  @Test
  public void testQuickfixSpecific() {
    doQuickfixTest("Create method 'onSort'");
  }

  @Test
  public void testQuickfixNoField() {
    doQuickfixTest("Create method 'onSort'");
  }

  @Test
  public void testQuickfixFieldType() {
    doQuickfixTest("Change field 'table' type to 'TableView<Map<String, Double>>'");
  }

  @Test
  public void testQuickfixNoFieldNested() {
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(javaFx.get().getFixture().getProject());
    settings.INSERT_INNER_CLASS_IMPORTS = true;
    doQuickfixTest("Create method 'onColumnEditStart'");
  }

  @Test
  public void testQuickfixSuper() {
    doQuickfixTest("Create method 'click'");
  }

  private void doHighlightingTest() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  private void doQuickfixTest(final String actionName) {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    String path = myFixture.getTestName(true) + ".fxml";
    myFixture.configureByFiles(path, myFixture.getTestName(false) + ".java");
    IntentionAction intention = myFixture.findSingleIntention(actionName);
    assertNotNull(intention);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(intention);
      myFixture.checkResultByFile(myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "_after.java", true);
    });
  }
}
