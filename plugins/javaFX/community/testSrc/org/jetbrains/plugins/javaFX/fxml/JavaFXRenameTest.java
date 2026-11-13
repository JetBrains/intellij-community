// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.openapi.impl.JavaRenameRefactoringImpl;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.refactoring.JavaFxPropertyRenameHandler;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class JavaFXRenameTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx =
    javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/rename/");

  @Test
  public void testCustomComponent() {
    doTest(javaFx.get().getTestName(false) + "1");
  }

  @Test
  public void testInRoot() {
    doTest(javaFx.get().getTestName(false) + "1");
  }

  @Test
  public void testControllerField() {
    doTest("newFieldName");
  }

  @Test
  public void testControllerFieldWithRefs() {
    doTest("newFieldName");
  }

  @Test
  public void testHandler() {
    doTest("newHandlerName");
  }

  @Test
  public void testPrivateSuperHandler() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    final String newName = "newHandlerName";
    final String fxmlPath = myFixture.getTestName(true) + ".fxml";
    final String fxmlPathAfter = myFixture.getTestName(true) + "_after.fxml";
    final String baseClassName = myFixture.getTestName(false) + "Base";

    myFixture.configureByFiles(baseClassName + ".java", myFixture.getTestName(false) + ".java", fxmlPath);

    EdtTestUtil.runInEdtAndWait(() -> {
      javaFx.get().doRenameWithAutomaticRenamers(newName);
      myFixture.checkResultByFile(fxmlPath, fxmlPathAfter, false);

      final PsiClass psiClass = myFixture.findClass(baseClassName);
      assertNotNull(psiClass);
      assertMethodExists(psiClass, newName);
    });
  }

  @Test
  public void testCustomComponentTag() {
    doTest("Foo", true);
  }

  @Test
  public void testCustomComponentPropertyTag() {
    doTest("Foo", true);
  }

  @Test
  public void testFromReference() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    final String newName = "lbl1";
    doTest(newName);
    ReadAction.runBlocking(() -> {
      final PsiClass controllerClass = myFixture.findClass(myFixture.getTestName(false));
      assertNotNull(controllerClass);
      assertFieldExists(controllerClass, newName);
    });
  }

  @Test
  public void testIdWithRefs() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiElement element = TargetElementUtil
        .findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
      assertNotNull(element);
      new RenameProcessor(myFixture.getProject(), element, "lb1", true, true).run();
      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.fxml");
    });
  }

  @Test
  public void testControllerBare() {
    doTestErrorHint("Foo", "Cannot rename built-in property");
  }

  @Test
  public void testControllerInExpr() {
    doTestErrorHint("Foo", "Cannot rename built-in property");
  }

  private void doTestErrorHint(String newName, String message) {
    try {
      doTest(newName, true);
      fail(message);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException expectedException) {
      assertEquals(message, expectedException.getMessage());
    }
  }

  @Test
  public void testPropertyRenameHandlerPresent() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    doTestPropertyRenameHandler(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testPropertyRenameHandlerPresentForStatic() {
    doTestPropertyRenameHandler(javaFx.get().getTestName(true) + ".fxml", "container/MyCustomContainer.java");
  }

  public void doTestPropertyRenameHandler(String... files) {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(files);
    ReadAction.runBlocking(() -> {
      final MapDataContext dataContext = new MapDataContext();
      dataContext.put(CommonDataKeys.EDITOR, myFixture.getEditor());
      final RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext);
      assertTrue(renameHandler instanceof JavaFxPropertyRenameHandler);
    });
  }

  @Test
  public void testStaticPropertyImportClass() {
    doTestStaticProperty("newPropName2", "container.MyCustomContainer");
  }

  @Test
  public void testStaticPropertyImportPackage() {
    doTestStaticProperty("newPropName2", "container.MyCustomContainer");
  }

  public void doTestStaticProperty(@NonNls String newName, String className) {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", className.replace('.', '/') + ".java");

    EdtTestUtil.runInEdtAndWait(() -> {
      final MapDataContext dataContext = new MapDataContext();
      dataContext.put(CommonDataKeys.EDITOR, myFixture.getEditor());
      dataContext.put(PsiElementRenameHandler.DEFAULT_NAME, newName);

      final JavaFxPropertyRenameHandler renameHandler = new JavaFxPropertyRenameHandler();
      assertTrue(renameHandler.isAvailableOnDataContext(dataContext));
      renameHandler.invoke(myFixture.getProject(), myFixture.getEditor(), null, dataContext);
      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.fxml");

      final PsiClass psiClass = myFixture.findClass(className);
      assertNotNull(psiClass);

      final String propName = newName.substring(0, 1).toUpperCase() + newName.substring(1);
      assertMethodExists(psiClass, "set" + propName);
    });
  }

  @Test
  public void testStaticPropertyMethod() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    final String className="container.MyCustomContainer";
    final String methodName = "setStaticProp";
    final String newName = "setNewMethodName";
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", className.replace('.', '/') + ".java");

    EdtTestUtil.runInEdtAndWait(() -> {
      final PsiClass psiClass = myFixture.findClass(className);
      assertNotNull(psiClass);
      final PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
      assertEquals(1, methods.length);
      final PsiMethod method = methods[0];

      final RenameRefactoring rename = new JavaRenameRefactoringImpl(myFixture.getProject(), method, newName, false, false);
      rename.run();

      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.fxml");
      assertMethodExists(psiClass, newName);
    });
  }

  @Test
  public void testStaticPropertyFromLibrary() {
    doTestErrorHint("Foo", RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.cannot.be.renamed")));
  }

  @Test
  public void testControllerMethod() {
    final PsiClass psiClass = doTestHandler("newName", null);
    ReadAction.runBlocking(() -> assertMethodExists(psiClass, "getNewName"));
  }

  @Test
  public void testNestedControllerIdFromFxml() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    doTestHandler("newName", myFixture.getTestName(false) + "Internal");
    myFixture.checkResultByFile(myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "_after.java", false);
  }

  @Test
  public void testNestedControllerIdFromJava() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "Internal.java", myFixture.getTestName(true) + ".fxml");
    EdtTestUtil.runInEdtAndWait(() -> {
      final PsiElement elementAtCaret = myFixture.getElementAtCaret();
      new RenameProcessor(myFixture.getProject(), elementAtCaret, "newName", false, false).run();
      myFixture.checkResultByFile(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(true) + "_after.fxml", false);
      myFixture.checkResultByFile(myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "_after.java", false);
    });
  }

  @Test
  public void testControllerStringProperty() {
    doTestProperty("newName", false);
  }

  @Test
  public void testControllerBooleanProperty() {
    doTestProperty("newName", true);
  }

  @Test
  public void testModelIdProperty() {
    doTestProperty("newName", "model.Data", false);
  }

  @Test
  public void testModelFieldProperty() {
    doTestProperty("newName", "model.Data", false);
  }

  @Test
  public void testResourceProperty() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(true) + ".properties");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.renameElementAtCaret("new.name");
      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.fxml");
      myFixture.checkResultByFile(myFixture.getTestName(true) + ".properties", myFixture.getTestName(true) + "_after.properties", false);
    });
  }

  public void doTestProperty(String name, boolean isBoolean) {
    doTestProperty(name, null, isBoolean);
  }

  public void doTestProperty(@NonNls String name, String className, boolean isBoolean) {
    final PsiClass psiClass = doTestHandler(name, className);
    ReadAction.runBlocking(() -> {
      final String propName = name.substring(0, 1).toUpperCase() + name.substring(1);
      assertMethodExists(psiClass, (isBoolean ? "is" : "get") + propName);
      assertMethodExists(psiClass, "set" + propName);
      assertMethodExists(psiClass, name + "Property");
      assertFieldExists(psiClass, name);
    });
  }

  @NotNull
  public PsiClass doTestHandler(String newName, String className) {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    if (className == null) {
      className = myFixture.getTestName(false);
      myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(false) + ".java");
    }
    else {
      myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(false) + ".java", className.replace('.', '/') + ".java");
    }
    final String finalClassName = className;
    return EdtTestUtil.runInEdtAndGet(() -> {
      final MapDataContext dataContext = new MapDataContext();
      dataContext.put(CommonDataKeys.EDITOR, myFixture.getEditor());
      dataContext.put(PsiElementRenameHandler.DEFAULT_NAME, newName);

      final JavaFxPropertyRenameHandler renameHandler = new JavaFxPropertyRenameHandler();
      assertTrue(renameHandler.isAvailableOnDataContext(dataContext));
      renameHandler.invoke(myFixture.getProject(), myFixture.getEditor(), null, dataContext);
      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.fxml");

      final PsiClass psiClass = myFixture.findClass(finalClassName);
      assertNotNull(psiClass);
      return psiClass;
    });
  }

  private static void assertFieldExists(PsiClass controllerClass, String name) {
    assertNotNull(controllerClass.findFieldByName(name, false), name);
  }

  private static void assertMethodExists(PsiClass controllerClass, String name) {
    final PsiMethod[] methods = controllerClass.findMethodsByName(name, false);
    assertThat(Arrays.stream(methods).map(PsiMethod::getName).toArray()).containsExactly(name);
  }

  private void doTest(final String newName) {
    doTest(newName, false);
  }

  private void doTest(final String newName, boolean inline) {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiElement element = TargetElementUtil
        .findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
      assertNotNull(element);
      if (inline) {
        Editor editorForElement = PsiEditorUtil.findEditor(element);
        CodeInsightTestUtil.doInlineRename(new MemberInplaceRenameHandler(), newName, ObjectUtils.notNull(editorForElement, myFixture.getEditor()), element);
      }
      else {
        new RenameProcessor(myFixture.getProject(), element, newName, true, true).run();
      }
      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.fxml");
    });
  }
}
