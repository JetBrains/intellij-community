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

import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 * @since 10.01.2013
 */
public class JavaFXHighlightingTest extends AbstractJavaFXTestCase {

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

  public void testRootTagCoercedUnchecked() throws Exception {
    doTest();
  }

  public void testStaticProperties() throws Exception {
    doTest();
  }

  public void testStaticPropertiesCustomLayout() throws Exception {
    myFixture.addClass("import javafx.scene.layout.GridPane;\n" +
                       "public class MyGridPane extends GridPane {}\n");
    myFixture.testHighlighting(false, false, false, getTestName(true) + ".fxml");
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
    myFixture.copyFileToProject("appIcon.png");
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    myFixture.testHighlighting(false, false, false, getTestName(true) + ".fxml");
  }

  public void testControllerIdRef() throws Exception {
    doTestIdController();
  }

  public void testEventHandlers() throws Exception {
    myFixture.testHighlighting(false, false, false, getTestName(true) + ".fxml");
  }

  public void testPackageLocalController() throws Exception {
    doTest(getTestName(false) + ".java");
  }

  public void testNoParamsHandler() throws Exception {
    doTest(getTestName(false) + ".java");
  }

  private void doTestIdController() throws Exception {
    final String controllerClassName = getTestName(false) + "Controller";
    myFixture.configureByFiles(getTestName(true) + ".fxml", controllerClassName + ".java");
    final PsiClass controllerClass = myFixture.findClass(controllerClassName);
    assertNotNull(controllerClass);
    assertTrue(controllerClass.getFields().length > 0);
    final int offset = myFixture.getCaretOffset();
    final PsiReference reference = myFixture.getFile().findReferenceAt(offset);
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

  public void testCustomComponentFieldsWithSameProperties() throws Exception {
    doTest("custom/" + getTestName(true) + ".fxml", "custom/" + getTestName(false) + ".java");
  }

  public void testCustomComponent_Fields() throws Exception {
    doTest("custom/" + getTestName(true) + ".fxml", "custom/_CustomVBox.java");
  }

  public void testInjectedController() throws Exception {
    myFixture.copyFileToProject("injected/MyController.java");
    myFixture.copyFileToProject("injected/FooVBox.java");

    final RegistryValue registryValue = Registry.get("javafx.fxml.controller.from.loader");
    final boolean injectionAllowed = registryValue.asBoolean();
    try {
      registryValue.setValue(true);
      doTestNavigation("injected.MyController", "label", "injected/" + getTestName(true) + ".fxml");
    }
    finally {
      registryValue.setValue(injectionAllowed);
    }
  }

  public void testControllerInExpression() throws Exception{
    doTest(getTestName(true) + ".fxml", getTestName(false) + ".java", getTestName(false) + "Wrapper.java");
  }

  public void testNamedColor() throws Exception {
    doTestNavigation(JavaFxCommonNames.JAVAFX_SCENE_COLOR, "ORANGE");
  }

  private void doTestNavigation(String resultClassName, String resultFieldName) throws Exception {
    doTestNavigation(resultClassName, resultFieldName, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void doTestNavigation(String resultClassName, String resultFieldName, String... additionalPaths) throws Exception {
    if (additionalPaths.length == 0) {
      myFixture.configureByFiles(getTestName(true) + ".fxml");
    } else {
      myFixture.configureByFiles(additionalPaths);
    }
    final int offset = myFixture.getCaretOffset();
    final PsiReference reference = myFixture.getFile().findReferenceAt(offset);
    assertNotNull(reference);
    final PsiClass resultClass = myFixture.getJavaFacade().findClass(resultClassName, GlobalSearchScope.allScope(getProject()));
    assertNotNull("Class " + resultClassName + " not found", resultClass);
    final PsiField resultField = resultClass.findFieldByName(resultFieldName, false);
    assertNotNull(resultField);
    assertEquals(resultField, reference.resolve());
  }

  public void testNavigationFromMainToFxml() throws Exception {
    myFixture.configureByFiles(getTestName(false) + ".java", getTestName(true) + ".fxml");
    final int offset = myFixture.getCaretOffset();
    final PsiReference reference = myFixture.getFile().findReferenceAt(offset);
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

  public void testIdOutOfHierarchy() throws Exception {
    doTest("btn.fxml", "MyController.java");
  }

  public void testIncludeBtn() throws Exception {
    doTest("btn.fxml");
  }

  public void testWrongBindingType() throws Exception {
    doTest(getTestName(false) + ".java");
  }

  public void testAllowIncludeTagInsideDefine() throws Exception {
    doTest("btn.fxml");
  }

  public void testValueOfAcceptance() throws Exception {
    doTest();
  }

  public void testInstantiationAcceptance() throws Exception {
    doTest();
  }

  public void testInstantiationAcceptanceWithNameArg() throws Exception {
    myFixture.addClass("package p;\n" +
                       "public class Root extends javafx.scene.layout.GridPane{\n" +
                       "  public Root(@javafx.beans.NamedArg(\"axis\") javafx.scene.Node node ) {\n" +
                       "    super(node);\n" +
                       "  }\n" +
                       "  public javafx.beans.property.Property<javafx.scene.Node> axis;" +
                       "  public void setAxis() {}" + 
                       "} ");
    doTest(getTestName(true) + ".fxml");
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

  public void testRootTagWithImport() throws Exception {
    doTest();
  }

  public void testReadOnly() throws Exception {
    doTest();
  }

  public void testReadOnly1() throws Exception {
    doTest();
  }

  public void testScriptSource() throws Exception {
    doTest("s1.js");
  }

  public void testPropertyNameExpression() throws Exception {
    doTest();
  }

  public void testPropertyChainExpression() throws Exception {
    doTest();
  }

  public void testIncorrectPropertyExpressionSyntax() throws Exception {
    doTest();
  }

  private void doTest(String additionalPath) {
    myFixture.configureByFiles(getTestName(true) + ".fxml", additionalPath);
    myFixture.testHighlighting(false, false, false, getTestName(true) + ".fxml");
  }

  private void doTest(String... paths) {
    myFixture.configureByFiles(paths);
    myFixture.testHighlighting(false, false, false, paths[0]);
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

  public void testShortNamesInRootType() throws Exception {
    doTest();
  }

  public void testLineChartInstantiation() throws Exception {
    doTest();
  }

  public void testUnexpectedNode() throws Exception {
    doTest();
  }

  public void testDefaultPropertyField() throws Exception {
    doTest();
  }

  public void testPrimitiveSubtags() throws Exception {
    doTest();
  }

  public void testReferencePosition() throws Exception {
    doTest();
  }

  public void testAcceptReferenceInsideDefine() throws Exception {
    doTest();
  }

  public void testRootTagOnDifferentLevels() throws Exception {
    doTest();
  }

  public void testAbsenceOfDefineAttributes() throws Exception {
    doTest();
  }

  public void testCopyReference() throws Exception {
    doTest();
  }

  public void testConstantValue() throws Exception {
    doTest();
  }

  public void testEnumConstantValue() throws Exception {
    doTest();
  }

  public void testNestedClassConstants() throws Exception {
    doTest("model/" + getTestName(false) + "Model.java");
  }

  public void testBoxedConstantValue() throws Exception {
    doTest();
  }

  public void testLiteralValue() throws Exception {
    doTest();
  }

  public void testNullObjectValue() throws Exception {
    doTest();
  }

  public void testNullPrimitiveValue() throws Exception {
    doTest();
  }

  public void testFactoryMethod() throws Exception {
    doTest();
  }

  public void testPrivateControllerMethod() throws Exception {
    doTest(getTestName(false) + ".java");
  }

  public void testPropertyTagCompatibleClass() throws Exception {
    doTest();
  }

  public void testPropertyTagCompatiblePrimitive() throws Exception {
    doTest();
  }

  public void testPropertyTagIncompatibleClass() throws Exception {
    doTest();
  }

  public void testPropertyTagIncompatiblePrimitive() throws Exception {
    doTest();
  }

  public void testPropertyTagUnrelatedClass() throws Exception {
    doWarningsTest();
  }

  public void testPropertyTagUnrelatedPrimitive() throws Exception {
    doWarningsTest();
  }

  public void testCharsetInInclude() throws Exception {
    myFixture.addFileToProject("sample.fxml", "<?import javafx.scene.layout.GridPane?>\n" +
                                                 "<fx:root type=\"javafx.scene.layout.GridPane\" xmlns:fx=\"http://javafx.com/fxml\"/>\n");
    doWarningsTest();
  }

  public void testIncludedForm() throws Exception {
    myFixture.addFileToProject("sample.fxml", "<?import javafx.scene.layout.GridPane?>\n" +
                                              "<fx:root type=\"javafx.scene.layout.GridPane\" xmlns:fx=\"http://javafx.com/fxml\"/>\n");
    doWarningsTest();
  }
  
  public void testInjectedControllerFields() throws Exception {
    myFixture.addFileToProject("sample.fxml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                              "<?import javafx.scene.control.*?>\n" +
                                              "<?import javafx.scene.layout.*?>\n" +
                                              "<AnchorPane id=\"AnchorPane\" xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + getTestName(false)+ "\">\n" +
                                              "  <Button fx:id=\"id1\" />\n" +
                                              "</AnchorPane>\n");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testAbsoluteRemoteUrl() throws Exception {
    doTest();
  }

  public void testMultipleStylesheetsAttribute() throws Exception {
    myFixture.addFileToProject("mystyle.css", ".myStyle {}");
    myFixture.addFileToProject("very/deeply/located/small.css", ".small {}");
    doTest();
  }

  public void testMultipleStylesheetsTag() throws Exception {
    myFixture.addFileToProject("mystyle.css", ".myStyle {}");
    myFixture.addFileToProject("very/deeply/located/small.css", ".small {}");
    doTest();
  }

  public void testPrivateEventHandler() throws Exception {
    myFixture.configureByFiles(getTestName(true) + ".fxml", getTestName(false) + ".java");
    myFixture.testHighlighting(true, true, true, getTestName(false) + ".java");
  }

  public void testFxIdInSuperclass() throws Exception {
    doTestControllerSuperclass();
  }

  public void testEventHandlerInSuperclass() throws Exception {
    doTestControllerSuperclass();
  }

  private void doTestControllerSuperclass() {
    final String superclass = getTestName(false);
    myFixture.copyFileToProject(superclass + ".java");
    myFixture.addClass("public class SubclassingController extends " + superclass + " {}");
    myFixture.addFileToProject("sample.fxml", "<?import javafx.scene.layout.VBox?>\n" +
                                              "<?import javafx.scene.control.Button?>\n" +
                                              "<VBox xmlns:fx=\"http://javafx.com/fxml/1\" xmlns=\"http://javafx.com/javafx/8\"\n" +
                                              "      fx:controller=\"SubclassingController\">\n" +
                                              "    <Button fx:id=\"inheritedButton\" onAction=\"#onAction\"/>\n" +
                                              "</VBox>");

    myFixture.testHighlighting(true, true, true, superclass + ".java");
  }

  public void testResourceKeyInAttribute() throws Exception {
    myFixture.addFileToProject("messages.properties", "string.key=My text\n" +
                                                      "double.key=123.456\n");
    doTest();
  }

  private void doTest() throws Exception {
    myFixture.testHighlighting(false, false, false, getTestName(true) + ".fxml");
  }

  private void doWarningsTest() {
    myFixture.testHighlighting(true, false, false, getTestName(true) + ".fxml");
  }

  @Override
  protected void enableInspections() {
    myFixture.enableInspections(new XmlPathReferenceInspection(), 
                                new RequiredAttributesInspection(), 
                                new UnusedDeclarationInspectionBase(true));
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/highlighting/";
  }
}
