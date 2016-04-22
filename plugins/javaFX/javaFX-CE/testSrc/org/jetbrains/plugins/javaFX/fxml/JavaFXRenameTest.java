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
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.refactoring.JavaFxPropertyRenameHandler;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class JavaFXRenameTest extends DaemonAnalyzerTestCase {
  @Override
  protected void setUpModule() {
    super.setUpModule();
    AbstractJavaFXTestCase.addJavaFxJarAsLibrary(getModule());
  }

  public void testCustomComponent() throws Exception {
    doTest(getTestName(false) + "1");
  }

  public void testInRoot() throws Exception {
    doTest(getTestName(false) + "1");
  }

  public void testControllerField() throws Exception {
    doTest("newFieldName");
  }

  public void testControllerFieldWithRefs() throws Exception {
    doTest("newFieldName");
  }

  public void testHandler() throws Exception {
    doTest("newHandlerName");
  }

  public void testCustomComponentTag() throws Exception {
    doTest("Foo", true);
  }

  public void testCustomComponentPropertyTag() throws Exception {
    doTest("Foo", true);
  }

  public void testFromReference() throws Exception {
    final String newName = "lbl1";
    doTest(newName);
    final PsiClass controllerClass = findClass(getTestName(false));
    assertNotNull(controllerClass);
    assertFieldExists(controllerClass, newName);
  }

  public void testIdWithRefs() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml");
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    new RenameProcessor(getProject(), element, "lb1", true, true).run();
    checkResultByFile(getTestName(true) + "_after.fxml");
  }

  public void testControllerBare() throws Exception {
    doTestErrorHint("Foo", "Cannot rename built-in property");
  }

  public void testControllerInExpr() throws Exception {
    doTestErrorHint("Foo", "Cannot rename built-in property");
  }

  private void doTestErrorHint(String newName, String message) throws Exception {
    try {
      doTest(newName, true);
      fail(message);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException expectedException) {
      assertEquals(message, expectedException.getMessage());
    }
  }

  public void testPropertyRenameHandlerPresent() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", getTestName(false) + ".java");
    final MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.EDITOR, myEditor);
    final RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext);
    assertTrue(renameHandler instanceof JavaFxPropertyRenameHandler);
  }

  public void testControllerMethod() throws Exception {
    final PsiClass psiClass = doTestHandler("newName", null);
    assertMethodExists(psiClass, "getNewName");
  }

  public void testControllerStringProperty() throws Exception {
    doTestProperty("newName", false);
  }

  public void testControllerBooleanProperty() throws Exception {
    doTestProperty("newName", true);
  }

  public void testModelIdProperty() throws Exception {
    doTestProperty("newName", "model.Data", false);
  }

  public void testModelFieldProperty() throws Exception {
    doTestProperty("newName", "model.Data", false);
  }

  public void doTestProperty(String name, boolean isBoolean) throws Exception {
    doTestProperty(name, null, isBoolean);
  }

  public void doTestProperty(String name, String className, boolean isBoolean) throws Exception {
    final PsiClass psiClass = doTestHandler(name, className);
    final String propName = name.substring(0, 1).toUpperCase(Locale.ENGLISH) + name.substring(1);
    assertMethodExists(psiClass, (isBoolean ? "is" : "get") + propName);
    assertMethodExists(psiClass, "set" + propName);
    assertMethodExists(psiClass, name + "Property");
    assertFieldExists(psiClass, name);
  }

  @NotNull
  public PsiClass doTestHandler(String newName, String className) throws Exception {
    if (className == null) {
      className = getTestName(false);
      configureByFiles(null, getTestName(true) + ".fxml", getTestName(false) + ".java");
    }
    else {
      configureByFiles(null, getTestName(true) + ".fxml", getTestName(false) + ".java", className.replace('.', '/') + ".java");
    }
    final MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.EDITOR, myEditor);
    dataContext.put(PsiElementRenameHandler.DEFAULT_NAME, newName);

    final JavaFxPropertyRenameHandler renameHandler = new JavaFxPropertyRenameHandler();
    assertTrue(renameHandler.isAvailableOnDataContext(dataContext));
    renameHandler.invoke(myProject, myEditor, null, dataContext);
    checkResultByFile(getTestName(true) + "_after.fxml");

    final PsiClass psiClass = findClass(className);
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

  private void doTest(final String newName) throws Exception {
    doTest(newName, false);
  }

  private void doTest(final String newName, boolean inline) throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    if (inline) {
      CodeInsightTestUtil.doInlineRename(new MemberInplaceRenameHandler(), newName, getEditor(), element);
    } else {
      new RenameProcessor(getProject(), element, newName, true, true).run();
    }
    checkResultByFile(getTestName(true) + "_after.fxml");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/rename/";
  }
}
