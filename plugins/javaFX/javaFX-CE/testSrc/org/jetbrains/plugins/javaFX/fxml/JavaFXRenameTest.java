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

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.openapi.impl.JavaRenameRefactoringImpl;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.refactoring.JavaFxPropertyRenameHandler;

import java.util.Arrays;

public class JavaFXRenameTest extends AbstractJavaFXRenameTest {

  public void testCustomComponent() {
    doTest(getTestName(false) + "1");
  }

  public void testInRoot() {
    doTest(getTestName(false) + "1");
  }

  public void testControllerField() {
    doTest("newFieldName");
  }

  public void testControllerFieldWithRefs() {
    doTest("newFieldName");
  }

  public void testHandler() {
    doTest("newHandlerName");
  }

  public void testPrivateSuperHandler() {
    final String newName = "newHandlerName";
    final String fxmlPath = getTestName(true) + ".fxml";
    final String fxmlPathAfter = getTestName(true) + "_after.fxml";
    final String baseClassName = getTestName(false) + "Base";

    myFixture.configureByFiles(baseClassName + ".java", getTestName(false) + ".java", fxmlPath);

    doRenameWithAutomaticRenamers(newName);
    myFixture.checkResultByFile(fxmlPath, fxmlPathAfter, false);

    final PsiClass psiClass = myFixture.findClass(baseClassName);
    assertNotNull(psiClass);
    assertMethodExists(psiClass, newName);
  }

  public void testCustomComponentTag() {
    doTest("Foo", true);
  }

  public void testCustomComponentPropertyTag() {
    doTest("Foo", true);
  }

  public void testFromReference() {
    final String newName = "lbl1";
    doTest(newName);
    final PsiClass controllerClass = myFixture.findClass(getTestName(false));
    assertNotNull(controllerClass);
    assertFieldExists(controllerClass, newName);
  }

  public void testIdWithRefs() {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    PsiElement element = TargetElementUtil
      .findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    new RenameProcessor(getProject(), element, "lb1", true, true).run();
    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");
  }

  public void testControllerBare() {
    doTestErrorHint("Foo", "Cannot rename built-in property");
  }

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

  public void testPropertyRenameHandlerPresent() {
    doTestPropertyRenameHandler(getTestName(true) + ".fxml", getTestName(false) + ".java");
  }

  public void testPropertyRenameHandlerPresentForStatic() {
    doTestPropertyRenameHandler(getTestName(true) + ".fxml", "container/MyCustomContainer.java");
  }

  public void doTestPropertyRenameHandler(String... files) {
    myFixture.configureByFiles(files);
    final MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.EDITOR, getEditor());
    final RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext);
    assertTrue(renameHandler instanceof JavaFxPropertyRenameHandler);
  }

  public void testStaticPropertyImportClass() {
    doTestStaticProperty("newPropName2", "container.MyCustomContainer");
  }

  public void testStaticPropertyImportPackage() {
    doTestStaticProperty("newPropName2", "container.MyCustomContainer");
  }

  public void doTestStaticProperty(@NonNls String newName, String className) {
    myFixture.configureByFiles(getTestName(true) + ".fxml", className.replace('.', '/') + ".java");

    final MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.EDITOR, getEditor());
    dataContext.put(PsiElementRenameHandler.DEFAULT_NAME, newName);

    final JavaFxPropertyRenameHandler renameHandler = new JavaFxPropertyRenameHandler();
    assertTrue(renameHandler.isAvailableOnDataContext(dataContext));
    renameHandler.invoke(getProject(), getEditor(), null, dataContext);
    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");

    final PsiClass psiClass = myFixture.findClass(className);
    assertNotNull(psiClass);

    final String propName = newName.substring(0, 1).toUpperCase() + newName.substring(1);
    assertMethodExists(psiClass, "set" + propName);
  }

  public void testStaticPropertyMethod() {
    final String className="container.MyCustomContainer";
    final String methodName = "setStaticProp";
    final String newName = "setNewMethodName";
    myFixture.configureByFiles(getTestName(true) + ".fxml", className.replace('.', '/') + ".java");

    final PsiClass psiClass = myFixture.findClass(className);
    assertNotNull(psiClass);
    final PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
    assertEquals(1, methods.length);
    final PsiMethod method = methods[0];

    final RenameRefactoring rename = new JavaRenameRefactoringImpl(getProject(), method, newName, false, false);
    rename.run();

    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");
    assertMethodExists(psiClass, newName);
  }

  public void testStaticPropertyFromLibrary() {
    doTestErrorHint("Foo", RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.cannot.be.renamed")));
  }

  public void testControllerMethod() {
    final PsiClass psiClass = doTestHandler("newName", null);
    assertMethodExists(psiClass, "getNewName");
  }

  public void testNestedControllerIdFromFxml() {
    doTestHandler("newName", getTestName(false) + "Internal");
    myFixture.checkResultByFile(getTestName(false) + ".java", getTestName(false) + "_after.java", false);
  }

  public void testNestedControllerIdFromJava() {
    myFixture.configureByFiles(getTestName(false) + ".java", getTestName(false) + "Internal.java", getTestName(true) + ".fxml");
    final PsiElement elementAtCaret = myFixture.getElementAtCaret();
    new RenameProcessor(getProject(), elementAtCaret, "newName", false, false).run();
    myFixture.checkResultByFile(getTestName(true) + ".fxml", getTestName(true) + "_after.fxml", false);
    myFixture.checkResultByFile(getTestName(false) + ".java", getTestName(false) + "_after.java", false);
  }

  public void testControllerStringProperty() {
    doTestProperty("newName", false);
  }

  public void testControllerBooleanProperty() {
    doTestProperty("newName", true);
  }

  public void testModelIdProperty() {
    doTestProperty("newName", "model.Data", false);
  }

  public void testModelFieldProperty() {
    doTestProperty("newName", "model.Data", false);
  }

  public void testResourceProperty() {
    myFixture.configureByFiles(getTestName(true) + ".fxml", getTestName(true) + ".properties");
    myFixture.renameElementAtCaret("new.name");
    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");
    myFixture.checkResultByFile(getTestName(true) + ".properties", getTestName(true) + "_after.properties", false);
  }

  public void doTestProperty(String name, boolean isBoolean) {
    doTestProperty(name, null, isBoolean);
  }

  public void doTestProperty(@NonNls String name, String className, boolean isBoolean) {
    final PsiClass psiClass = doTestHandler(name, className);
    final String propName = name.substring(0, 1).toUpperCase() + name.substring(1);
    assertMethodExists(psiClass, (isBoolean ? "is" : "get") + propName);
    assertMethodExists(psiClass, "set" + propName);
    assertMethodExists(psiClass, name + "Property");
    assertFieldExists(psiClass, name);
  }

  @NotNull
  public PsiClass doTestHandler(String newName, String className) {
    if (className == null) {
      className = getTestName(false);
      myFixture.configureByFiles(getTestName(true) + ".fxml", getTestName(false) + ".java");
    }
    else {
      myFixture.configureByFiles(getTestName(true) + ".fxml", getTestName(false) + ".java", className.replace('.', '/') + ".java");
    }
    final MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.EDITOR, getEditor());
    dataContext.put(PsiElementRenameHandler.DEFAULT_NAME, newName);

    final JavaFxPropertyRenameHandler renameHandler = new JavaFxPropertyRenameHandler();
    assertTrue(renameHandler.isAvailableOnDataContext(dataContext));
    renameHandler.invoke(getProject(), getEditor(), null, dataContext);
    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");

    final PsiClass psiClass = myFixture.findClass(className);
    assertNotNull(psiClass);
    return psiClass;
  }

  private static void assertFieldExists(PsiClass controllerClass, String name) {
    assertNotNull(name, controllerClass.findFieldByName(name, false));
  }

  private static void assertMethodExists(PsiClass controllerClass, String name) {
    final PsiMethod[] methods = controllerClass.findMethodsByName(name, false);
    assertOrderedEquals(Arrays.stream(methods).map(PsiMethod::getName).toArray(), name);
  }

  private void doTest(final String newName) {
    doTest(newName, false);
  }

  private void doTest(final String newName, boolean inline) {
    myFixture.configureByFiles(getTestName(true) + ".fxml", getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
      .findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    if (inline) {
      CodeInsightTestUtil.doInlineRename(new MemberInplaceRenameHandler(), newName, getEditor(), element);
    } else {
      new RenameProcessor(getProject(), element, newName, true, true).run();
    }
    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/rename/";
  }
}
