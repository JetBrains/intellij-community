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
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxUnresolvedFxIdReferenceInspection;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions.JavaFxInjectPageLanguageIntention;

import java.util.Set;

public class JavaFXQuickfixTest extends LightCodeInsightFixtureTestCase {
  public static final DefaultLightProjectDescriptor JAVA_FX_WITH_GROOVY_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      AbstractJavaFXTestCase.addJavaFxJarAsLibrary(module, model);
      PsiTestUtil.addLibrary(module, model, "javafx", PluginPathManager.getPluginHomePath("javaFX") + "/testData", "groovy-1.8.0.jar");
      super.configureModule(module, model, contentEntry);
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_FX_WITH_GROOVY_DESCRIPTOR;
  }

  public void testCreateControllerMethod() throws Exception {
    doTest("Create method 'void bar(ActionEvent)'", ".java");
  }

  public void testCreateControllerMethodInGroovy() throws Exception {
    doTest("Create method 'void bar(ActionEvent)'", ".groovy");
  }

  public void testCreateControllerMethodGeneric() throws Exception {
    doTest("Create method 'void onSort(SortEvent)'", ".java");
  }

  public void testCreateControllerMethodHalfRaw() throws Exception {
    doTest("Create method 'void onSort(SortEvent)'", ".java");
  }

  public void testCreateFieldPublicVisibility() throws Exception {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PUBLIC, ".java");
  }

  public void testCreateFieldProtectedVisibility() throws Exception {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PROTECTED, ".java");
  }

  public void testCreateFieldPrivateVisibility() throws Exception {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PRIVATE, ".java");
  }

  public void testCreateFieldPackageLocalVisibility() throws Exception {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PACKAGE_LOCAL, ".java");
  }

  public void testCreateFieldEscalateVisibility() throws Exception {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", VisibilityUtil.ESCALATE_VISIBILITY, ".java");
  }

  public void testCreateMethodPublicVisibility() throws Exception {
    doTestWithDefaultVisibility("Create method 'void onAction(ActionEvent)'", "CreateMethod", PsiModifier.PUBLIC, ".java");
  }

  public void testCreateMethodProtectedVisibility() throws Exception {
    doTestWithDefaultVisibility("Create method 'void onAction(ActionEvent)'", "CreateMethod", PsiModifier.PROTECTED, ".java");
  }

  public void testCreateMethodPrivateVisibility() throws Exception {
    doTestWithDefaultVisibility("Create method 'void onAction(ActionEvent)'", "CreateMethod", PsiModifier.PRIVATE, ".java");
  }

  public void testCreateMethodPackageLocalVisibility() throws Exception {
    doTestWithDefaultVisibility("Create method 'void onAction(ActionEvent)'", "CreateMethod", PsiModifier.PACKAGE_LOCAL, ".java");
  }

  public void testCreateMethodEscalateVisibility() throws Exception {
    doTestWithDefaultVisibility("Create method 'void onAction(ActionEvent)'", "CreateMethod", VisibilityUtil.ESCALATE_VISIBILITY,
                                ".java");
  }

  public void testCreateFieldEmptyName() throws Exception {
    String path = getTestName(true) + ".fxml";
    final IntentionAction intention =
      myFixture.getAvailableIntention("Create field 'btn'", path, getTestName(false) + ".java");
    assertNull(intention);
  }

  public void testRegisterPageLanguage() throws Exception {
    myFixture.configureByFile(getTestName(true) + ".fxml");
    final IntentionAction intention = myFixture.findSingleIntention("Specify page language");
    assertNotNull(intention);
    Set<String> languages = JavaFxInjectPageLanguageIntention.getAvailableLanguages(getProject());
    assertContainsElements(languages, "groovy");
    JavaFxInjectPageLanguageIntention languageIntention =
      (JavaFxInjectPageLanguageIntention)((IntentionActionDelegate)intention).getDelegate();
    languageIntention.registerPageLanguage(getProject(), (XmlFile)myFixture.getFile(), "groovy");
    myFixture.checkResultByFile(getTestName(true) + ".fxml", getTestName(true) + "_after.fxml", true);
  }

  public void testWrapWithDefine() throws Exception {
    final IntentionAction intention =
      myFixture.getAvailableIntention("Wrap \"lb\" with fx:define", getTestName(true) + ".fxml");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");
  }

  private void doTestWithDefaultVisibility(final String actionName,
                                           final String inputName,
                                           final String defaultVisibility,
                                           final String extension) throws Exception {
    JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    String savedVisibility = settings.VISIBILITY;
    try {
      settings.VISIBILITY = defaultVisibility;
      doTest(actionName, inputName, getTestName(false), extension);
    }
    finally {
      settings.VISIBILITY = savedVisibility;
    }
  }

  private void doTest(final String actionName, final String extension) throws Exception {
    doTest(actionName, getTestName(false), getTestName(false), extension);
  }

  private void doTest(final String actionName, final String inputName, final String outputName, final String extension) throws Exception {
    String path = PlatformTestUtil.lowercaseFirstLetter(inputName, true) + ".fxml";
    final IntentionAction intention =
      myFixture.getAvailableIntention(actionName, path, inputName + extension);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(inputName + extension, outputName + "_after" + extension, true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new JavaFxUnresolvedFxIdReferenceInspection());
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/quickfix/";
  }
}
