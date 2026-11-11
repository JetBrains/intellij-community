// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.VisibilityUtil;
import com.sun.tools.javac.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections.JavaFxUnresolvedFxIdReferenceInspection;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions.JavaFxInjectPageLanguageIntention;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions.JavaFxScriptEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class JavaFXQuickfixTest {
  public static final DefaultLightProjectDescriptor JAVA_FX_WITH_GROOVY_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      AbstractJavaFXTestCase.addJavaFxJarAsLibrary(model);
      PsiTestUtil.addLibrary(model, "javafx", PluginPathManager.getPluginHomePath("javaFX") + "/testData", "groovy-1.8.0.jar");
      super.configureModule(module, model, contentEntry);
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JAVA_FX_WITH_GROOVY_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testCreateControllerMethodEmptyName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String inputName = myFixture.getTestName(false);
    String extension = ".java";
    String actionName = "Create method";

    String path = PlatformTestUtil.lowercaseFirstLetter(inputName, true) + ".fxml";
    myFixture.configureByFiles(path, inputName + extension);
    IntentionAction intention = myFixture.findSingleIntention(actionName);

    Editor editor = myFixture.getEditor();
    PsiFile file = myFixture.getFile();
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      Document document = editor.getDocument();

      PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
      TextRange range = PsiTreeUtil.getParentOfType(leaf, XmlAttributeValue.class).getValueTextRange();
      document.deleteString(range.getStartOffset(), range.getEndOffset());

      PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(document);
    });
    assertFalse(EdtTestUtil.runInEdtAndGet(() -> intention.isAvailable(myFixture.getProject(), editor, file)));
  }

  @Test
  public void testCreateControllerMethod() {
    doTest("Create method 'bar'", ".java");
  }

  @Test
  public void testCreateControllerMethodInGroovy() {
    doTest("Create method 'bar'", ".groovy");
  }

  @Test
  public void testCreateControllerMethodGeneric() {
    doTest("Create method 'onSort'", ".java");
  }

  @Test
  public void testCreateControllerMethodHalfRaw() {
    doTest("Create method 'onSort'", ".java");
  }

  @Test
  public void testCreateFieldPublicVisibility() {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PUBLIC, ".java");
  }

  @Test
  public void testCreateFieldProtectedVisibility() {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PROTECTED, ".java");
  }

  @Test
  public void testCreateFieldPrivateVisibility() {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PRIVATE, ".java");
  }

  @Test
  public void testCreateFieldPackageLocalVisibility() {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", PsiModifier.PACKAGE_LOCAL, ".java");
  }

  @Test
  public void testCreateFieldEscalateVisibility() {
    doTestWithDefaultVisibility("Create field 'btn'", "CreateField", VisibilityUtil.ESCALATE_VISIBILITY, ".java");
  }

  @Test
  public void testCreateMethodPublicVisibility() {
    doTestWithDefaultVisibility("Create method 'onAction'", "CreateMethod", PsiModifier.PUBLIC, ".java");
  }

  @Test
  public void testCreateMethodProtectedVisibility() {
    doTestWithDefaultVisibility("Create method 'onAction'", "CreateMethod", PsiModifier.PROTECTED, ".java");
  }

  @Test
  public void testCreateMethodPrivateVisibility() {
    doTestWithDefaultVisibility("Create method 'onAction'", "CreateMethod", PsiModifier.PRIVATE, ".java");
  }

  @Test
  public void testCreateMethodPackageLocalVisibility() {
    doTestWithDefaultVisibility("Create method 'onAction'", "CreateMethod", PsiModifier.PACKAGE_LOCAL, ".java");
  }

  @Test
  public void testCreateMethodEscalateVisibility() {
    doTestWithDefaultVisibility("Create method 'onAction'", "CreateMethod", VisibilityUtil.ESCALATE_VISIBILITY,
                                ".java");
  }

  @Test
  public void testCreateFieldEmptyName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String path = myFixture.getTestName(true) + ".fxml";
    final IntentionAction intention =
      myFixture.getAvailableIntention("Create field 'btn'", path, myFixture.getTestName(false) + ".java");
    assertNull(intention);
  }

  @Test
  public void testRegisterPageLanguage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(true) + ".fxml");
    final IntentionAction intention = myFixture.findSingleIntention("Specify page language");
    assertNotNull(intention);
    Set<String> languages = findEngineNames();
    assertThat(languages).contains("groovy");
    JavaFxInjectPageLanguageIntention languageIntention = (JavaFxInjectPageLanguageIntention)IntentionActionDelegate.unwrap(intention);
    languageIntention.registerPageLanguage(myFixture.getProject(), (XmlFile)myFixture.getFile(), "groovy");
    myFixture.checkResultByFile(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(true) + "_after.fxml", true);
  }

  @Test
  public void testRegisterPageLanguageAfterXmlDeclaration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(true) + ".fxml");
    JavaFxInjectPageLanguageIntention languageIntention =
      (JavaFxInjectPageLanguageIntention)IntentionActionDelegate.unwrap(myFixture.findSingleIntention("Specify page language"));
    languageIntention.registerPageLanguage(myFixture.getProject(), (XmlFile)myFixture.getFile(), "groovy");
    myFixture.checkResultByFile(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(true) + "_after.fxml", true);
  }

  @Test
  public void testAvailableLanguagesDoNotLoadProviderClasses(@TempDir Path tempDir) throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Path classesDir = Files.createDirectory(tempDir.resolve("javaFxScriptEngines"));
    assertEquals(0, Main.compile(new String[]{
      "-d", classesDir.toString(), myFixture.getTestDataPath() + "scriptEngineFactory/rocq/TestRocqScriptEngineFactory.java"}), "testData sources failed to compile");
    Path serviceFile = classesDir.resolve("META-INF/services/javax.script.ScriptEngineFactory");
    Files.createDirectories(serviceFile.getParent());
    Files.writeString(serviceFile, "# a comment\norg.example.MissingScriptEngineFactory\nrocq.TestRocqScriptEngineFactory # a comment\n");
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(classesDir);
    PsiTestUtil.addLibrary(myFixture.getTestRootDisposable(), myFixture.getModule(), "scriptEngines", classesDir.getParent().toString(),
                           classesDir.getFileName().toString());

    assertThat(findEngineNames()).hasSize(2).containsOnly("groovy", "rocq");
  }

  private Set<String> findEngineNames() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return EdtTestUtil.runInEdtAndGet(() -> {
      final AtomicReference<Set<String>> result = new AtomicReference<>();
      JavaFxScriptEngineService.getInstance(myFixture.getProject()).findEngineNames(result::set);
      PlatformTestUtil.waitWithEventsDispatching("The script engines are not found in time", () -> result.get() != null, 10);
      return result.get();
    });
  }

  @Test
  public void testWrapWithDefine() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final IntentionAction intention =
      myFixture.getAvailableIntention("Wrap \"lb\" with fx:define", myFixture.getTestName(true) + ".fxml");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.fxml");
  }

  private void doTestWithDefaultVisibility(final String actionName,
                                           final String inputName,
                                           final String defaultVisibility,
                                           final String extension) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).VISIBILITY = defaultVisibility;
    doTest(actionName, inputName, myFixture.getTestName(false), extension);
  }

  private void doTest(final String actionName, final String extension) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(actionName, myFixture.getTestName(false), myFixture.getTestName(false), extension);
  }

  private void doTest(final String actionName, final String inputName, @Nullable final String outputName, final String extension) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(PluginPathManager.getPluginHomePath("javaFX") + "/testData/quickfix/");
    myFixture.enableInspections(new JavaFxUnresolvedFxIdReferenceInspection());
  }
}
