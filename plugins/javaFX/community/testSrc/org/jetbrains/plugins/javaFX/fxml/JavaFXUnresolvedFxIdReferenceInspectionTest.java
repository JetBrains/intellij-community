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
import com.intellij.util.VisibilityUtil;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxUnresolvedFxIdReferenceInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaFXUnresolvedFxIdReferenceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx =
    javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/unresolvedFxId/",
                  () -> List.of(new JavaFxUnresolvedFxIdReferenceInspection()), tagName -> getHint(tagName));

  @Test
  public void testUnknownRef() {
    doTest("Controller", VisibilityUtil.ESCALATE_VISIBILITY);
  }

  @Test
  public void testRootType() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml");
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(getHint("unknown"));
    assertThat(intentionActions).isEmpty();
  }

  @Test
  public void testIncludeBtnWithController() {
    javaFx.get().getFixture().addFileToProject("btn.fxml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <?import javafx.scene.control.*?>
      <Button/>""");
    doTest("MyController", VisibilityUtil.ESCALATE_VISIBILITY);
  }

  @Test
  public void testFieldsFromControllerSuper() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.addClass("""
                         import javafx.scene.control.RadioButton;
                         public class SuperController {
                             public RadioButton option1;
                         }
                         """);
    myFixture.addClass("public class Controller extends SuperController {}");
    final String testFxml = myFixture.getTestName(true) + ".fxml";
    myFixture.configureByFile(testFxml);
    myFixture.testHighlighting(true, false, false, testFxml);
  }

  private void doTest(final String controllerName, final String defaultVisibility) {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(javaFx.get().getFixture().getProject());
    settings.VISIBILITY = defaultVisibility;
    doTest(controllerName);
  }

  private void doTest(final String controllerName) {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", controllerName + ".java");
    final IntentionAction singleIntention = myFixture.findSingleIntention(getHint("unknown"));
    assertNotNull(singleIntention);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(singleIntention);
      myFixture.checkResultByFile(controllerName + ".java", controllerName + "_after.java", true);
    });
  }

  private static String getHint(String tagName) {
    return "Create field '" + tagName + "'";
  }
}
