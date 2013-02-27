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

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 * @since 10.01.2013
 */
public class JavaFXHighlightingTest extends DaemonAnalyzerTestCase {
  @Override
  protected void setUpModule() {
    super.setUpModule();
    //noinspection SpellCheckingInspection
    PsiTestUtil.addLibrary(getModule(), "javafx", PluginPathManager.getPluginHomePath("javaFX") + "/testData", "jfxrt.jar");
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new XmlPathReferenceInspection(), new RequiredAttributesInspection() };
  }

  public void testLoginForm() throws Exception {
    doTest();
  }

  public void testUnresolvedTopLevelController() throws Exception {
    doTest();
  }

  public void testController() throws Exception {
    doTest();
  }

  public void testCoercedTypes() throws Exception {
    doTest();
  }

  public void testStaticProperties() throws Exception {
    doTest();
  }

  public void testEnumValues() throws Exception {
    doTest();
  }

  public void testDefaultTagProperties() throws Exception {
    doTest();
  }

  public void testDefaultTagInList() throws Exception {
    doTest();
  }

  public void testUnresolvedImport() throws Exception {
    doTest();
  }

  public void testImageIcon() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", "appIcon.png");
    doDoTest(false, false);
  }

  public void testControllerIdRef() throws Exception {
    doTestIdController();
  }

  public void testPackageLocalController() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", getTestName(false) + ".java");
    doDoTest(false, false);
  }

  public void testNoParamsHandler() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", getTestName(false) + ".java");
    doDoTest(false, false);
  }

  private void doTestIdController() throws Exception {
    final String controllerClassName = getTestName(false) + "Controller";
    configureByFiles(null, getTestName(true) + ".fxml", controllerClassName + ".java");
    final PsiClass controllerClass = findClass(controllerClassName);
    assertNotNull(controllerClass);
    assertTrue(controllerClass.getFields().length > 0);
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiReference reference = myFile.findReferenceAt(offset);
    assertNotNull(reference);
    assertEquals(controllerClass.getFields()[0], reference.resolve());
  }

  public void testConstantNavigation() throws Exception {
    doTestNavigation("java.lang.Double", "NEGATIVE_INFINITY");
  }

  public void testEnumNavigation() throws Exception {
    doTestNavigation("javafx.geometry.Pos", "CENTER");
  }

  public void testCustomComponentFields() throws Exception {
    doTestNavigation("CustomVBox", "tf", "custom/" + getTestName(true) + ".fxml", "custom/CustomVBox.java");
  }

  public void testCustomComponent_Fields() throws Exception {
    configureByFiles(null, "custom/" + getTestName(true) + ".fxml", "custom/_CustomVBox.java");
    doDoTest(false, false);
  }

  private void doTestNavigation(String resultClassName, String resultFieldName) throws Exception {
    doTestNavigation(resultClassName, resultFieldName, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void doTestNavigation(String resultClassName, String resultFieldName, String... additionalPaths) throws Exception {
    if (additionalPaths.length == 0) {
      configureByFiles(null, getTestName(true) + ".fxml");
    } else {
      configureByFiles(null, additionalPaths);
    }
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiReference reference = myFile.findReferenceAt(offset);
    assertNotNull(reference);
    final PsiClass resultClass = myJavaFacade.findClass(resultClassName, ProjectScope.getAllScope(getProject()));
    assertNotNull("Class " + resultClassName + " not found", resultClass);
    final PsiField resultField = resultClass.findFieldByName(resultFieldName, false);
    assertNotNull(resultField);
    assertEquals(resultField, reference.resolve());
  }

  public void testNavigationFromMainToFxml() throws Exception {
    configureByFiles(null, getTestName(false) + ".java", getTestName(true) + ".fxml");
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiReference reference = myFile.findReferenceAt(offset);
    assertNotNull(reference);
    final PsiElement resolve = reference.resolve();
    assertNotNull(resolve);
    final PsiFile containingFile = resolve.getContainingFile();
    assertNotNull(containingFile);
    assertEquals(getTestName(true) + ".fxml", containingFile.getName());
  }

  public void testSourceAttrRecognition() throws Exception {
    doTest();
  }

  public void testReferenceAttributes() throws Exception {
    doTest();
  }

  public void testVariables() throws Exception {
    doTest();
  }

  public void testDefineAttributes() throws Exception {
    doTest();
  }

  public void testDefinedElements() throws Exception {
    doTest();
  }

  public void testPropertyElementsWithAnyAttributes() throws Exception {
    doTest();
  }

  public void testHandlerWithoutController() throws Exception {
    doTest();
  }

  public void testHandlerWithoutPageLanguage() throws Exception {
    doTest();
  }

  public void testIncludeBtn() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", "btn.fxml");
    doDoTest(false, false);
  }

  public void testValueOfAcceptance() throws Exception {
    doTest();
  }

  public void testInstantiationAcceptance() throws Exception {
    doTest();
  }

  public void testFqnTagNames() throws Exception {
    doTest();
  }

  public void testRootTag() throws Exception {
    doTest();
  }

  public void testUnresolvedRootTag() throws Exception {
    doTest();
  }

  public void testRootTagWithoutType() throws Exception {
    doTest();
  }

  public void testReadOnly() throws Exception {
    doTest();
  }

  public void testScriptSource() throws Exception {
    configureByFiles(null, getTestName(true) + ".fxml", "s1.js");
    doDoTest(false, false);
  }

  public void testExpressionBinding() throws Exception {
    doTest();
  }

  public void testPropertyWithoutField() throws Exception {
    doTest();
  }

  public void testRootTagProperties() throws Exception {
    doTest();
  }

  public void testBooleanPropertyWithoutField() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    doTest(false, false, getTestName(true) + ".fxml");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/highlighting/";
  }
}
