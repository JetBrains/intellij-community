/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
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
    myFixture.addFileToProject("btn.fxml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                           "<?import javafx.scene.control.*?>\n" +
                                           "<Button/>");
    doTest("MyController", VisibilityUtil.ESCALATE_VISIBILITY);
  }

  public void testFieldsFromControllerSuper() {
    myFixture.addClass("import javafx.scene.control.RadioButton;\n" +
                       "public class SuperController {\n" +
                       "    public RadioButton option1;\n" +
                       "}\n");
    myFixture.addClass("public class Controller extends SuperController {}");
    final String testFxml = getTestName(true) + ".fxml";
    myFixture.configureByFile(testFxml);
    myFixture.testHighlighting(true, false, false, testFxml);
  }

  private void doTest(final String controllerName, final String defaultVisibility) {
    JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    String savedVisibility = settings.VISIBILITY;
    try {
      settings.VISIBILITY = defaultVisibility;
      doTest(controllerName);
    }
    finally {
      settings.VISIBILITY = savedVisibility;
    }
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
