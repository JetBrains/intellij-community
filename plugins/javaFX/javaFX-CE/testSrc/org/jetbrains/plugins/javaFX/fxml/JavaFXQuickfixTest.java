// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  public void testCreateControllerMethodEmptyName() {
    String inputName = getTestName(false);
    String extension = ".java";
    String actionName = "Create method";

    String path = PlatformTestUtil.lowercaseFirstLetter(inputName, true) + ".fxml";
    myFixture.configureByFiles(path, inputName + extension);
    IntentionAction intention = myFixture.findSingleIntention(actionName);

    Editor editor = myFixture.getEditor();
    PsiFile file = myFixture.getFile();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      Document document = editor.getDocument();

      PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
      TextRange range = PsiTreeUtil.getParentOfType(leaf, XmlAttributeValue.class).getValueTextRange();
      document.deleteString(range.getStartOffset(), range.getEndOffset());

      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    });
    assertFalse(intention.isAvailable(getProject(), editor, file));
  }

  public void testCreateControllerMethod() {
    doTest("Create method 'bar'", ".java");
  }

  public void testCreateControllerMethodInGroovy() {
    doTest("Create method 'bar'", ".groovy");
  }

  public void testCreateControllerMethodGeneric() {
    doTest("Create method 'onSort'", ".java");
  }

  public void testCreateControllerMethodHalfRaw() {
    doTest("Create method 'onSort'", ".java");
  }

  public void testCreateFieldPublicVisibility() {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PUBLIC, ".java");
  }

  public void testCreateFieldProtectedVisibility() {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PROTECTED, ".java");
  }

  public void testCreateFieldPrivateVisibility() {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PRIVATE, ".java");
  }

  public void testCreateFieldPackageLocalVisibility() {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PACKAGE_LOCAL, ".java");
  }

  public void testCreateFieldEscalateVisibility() {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", VisibilityUtil.ESCALATE_VISIBILITY, ".java");
  }

  public void testCreateMethodPublicVisibility() {
    doTestWithDefaultVisibility("Create method 'onAction'", "CreateMethod", PsiModifier.PUBLIC, ".java");
  }

  public void testCreateMethodProtectedVisibility() {
    doTestWithDefaultVisibility("Create method 'onAction'", "CreateMethod", PsiModifier.PROTECTED, ".java");
  }

  public void testCreateMethodPrivateVisibility() {
    doTestWithDefaultVisibility("Create method 'onAction'", "CreateMethod", PsiModifier.PRIVATE, ".java");
  }

  public void testCreateMethodPackageLocalVisibility() {
    doTestWithDefaultVisibility("Create method 'onAction'", "CreateMethod", PsiModifier.PACKAGE_LOCAL, ".java");
  }

  public void testCreateMethodEscalateVisibility() {
    doTestWithDefaultVisibility("Create method 'onAction'", "CreateMethod", VisibilityUtil.ESCALATE_VISIBILITY,
                                ".java");
  }

  public void testCreateFieldEmptyName() {
    String path = getTestName(true) + ".fxml";
    final IntentionAction intention =
      myFixture.getAvailableIntention("Create field 'btn'", path, getTestName(false) + ".java");
    assertNull(intention);
  }

  public void testRegisterPageLanguage() {
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

  public void testWrapWithDefine() {
    final IntentionAction intention =
      myFixture.getAvailableIntention("Wrap \"lb\" with fx:define", getTestName(true) + ".fxml");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");
  }

  private void doTestWithDefaultVisibility(final String actionName,
                                           final String inputName,
                                           final String defaultVisibility,
                                           final String extension) {
    JavaCodeStyleSettings.getInstance(getProject()).VISIBILITY = defaultVisibility;
    doTest(actionName, inputName, getTestName(false), extension);
  }

  private void doTest(final String actionName, final String extension) {
    doTest(actionName, getTestName(false), getTestName(false), extension);
  }

  private void doTest(final String actionName, final String inputName, @Nullable final String outputName, final String extension) {
    String path = PlatformTestUtil.lowercaseFirstLetter(inputName, true) + ".fxml";
    myFixture.configureByFiles(path, inputName + extension);

    if (outputName == null) {
      assertNull(myFixture.getAvailableIntention(actionName));
    }
    else {
      final IntentionAction intention = myFixture.findSingleIntention(actionName);
      myFixture.launchAction(intention);
      myFixture.checkResultByFile(inputName + extension, outputName + "_after" + extension, true);
    }
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
