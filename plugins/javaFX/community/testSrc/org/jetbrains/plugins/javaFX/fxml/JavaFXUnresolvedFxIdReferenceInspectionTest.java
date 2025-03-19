// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxUnresolvedFxIdReferenceInspection;

import java.util.List;

public class JavaFXUnresolvedFxIdReferenceInspectionTest extends AbstractJavaFXQuickFixTest {

  @Override
  protected void enableInspections() {
    myFixture.enableInspections(new JavaFxUnresolvedFxIdReferenceInspection());
  }

  public void testUnknownRef() {
    doTest("Controller", VisibilityUtil.ESCALATE_VISIBILITY);
  }

  public void testRootType() {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(getHint("unknown"));
    assertEmpty(intentionActions);
  }

  public void testIncludeBtnWithController() {
    myFixture.addFileToProject("btn.fxml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <?import javafx.scene.control.*?>
      <Button/>""");
    doTest("MyController", VisibilityUtil.ESCALATE_VISIBILITY);
  }

  public void testFieldsFromControllerSuper() {
    myFixture.addClass("""
                         import javafx.scene.control.RadioButton;
                         public class SuperController {
                             public RadioButton option1;
                         }
                         """);
    myFixture.addClass("public class Controller extends SuperController {}");
    final String testFxml = getTestName(true) + ".fxml";
    myFixture.configureByFile(testFxml);
    myFixture.testHighlighting(true, false, false, testFxml);
  }

  private void doTest(final String controllerName, final String defaultVisibility) {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.VISIBILITY = defaultVisibility;
    doTest(controllerName);
  }

  private void doTest(final String controllerName) {
    myFixture.configureByFiles(getTestName(true) + ".fxml", controllerName + ".java");
    final IntentionAction singleIntention = myFixture.findSingleIntention(getHint("unknown"));
    assertNotNull(singleIntention);
    myFixture.launchAction(singleIntention);
    myFixture.checkResultByFile(controllerName + ".java", controllerName + "_after.java", true);
  }

  @Override
  protected String getHint(String tagName) {
    return "Create field '" + tagName + "'";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/inspections/unresolvedFxId/";
  }
}
