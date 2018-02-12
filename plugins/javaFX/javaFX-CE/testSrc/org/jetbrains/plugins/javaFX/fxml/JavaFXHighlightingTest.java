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

  public void testLoginForm() {
    doTest();
  }

  public void testUnresolvedTopLevelController() {
    doTest();
  }

  public void testController() {
    doTest();
  }

  public void testCoercedTypes() {
    doTest();
  }

  public void testRootTagCoercedUnchecked() {
    doTest();
  }

  public void testStaticProperties() {
    doTest();
  }

  public void testStaticPropertiesCustomLayout() {
    myFixture.addClass("import javafx.scene.layout.GridPane;\n" +
                       "public class MyGridPane extends GridPane {}\n");
    myFixture.testHighlighting(false, false, false, getTestName(true) + ".fxml");
  }

  public void testEnumValues() {
    doTest();
  }

  public void testDefaultTagProperties() {
    doTest();
  }

  public void testDefaultTagInList() {
    doTest();
  }

  public void testUnresolvedImport() {
    doTest();
  }

  public void testImageIcon() {
    myFixture.copyFileToProject("appIcon.png");
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    myFixture.testHighlighting(false, false, false, getTestName(true) + ".fxml");
  }

  public void testControllerIdRef() {
    doTestIdController();
  }

  public void testEventHandlers() {
    myFixture.testHighlighting(false, false, false, getTestName(true) + ".fxml");
  }

  public void testPackageLocalController() {
    doTest(getTestName(false) + ".java");
  }

  public void testNoParamsHandler() {
    doTest(getTestName(false) + ".java");
  }

  private void doTestIdController() {
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

  public void testConstantNavigation() {
    doTestNavigation("java.lang.Double", "NEGATIVE_INFINITY");
  }

  public void testEnumNavigation() {
    doTestNavigation("javafx.geometry.Pos", "CENTER");
  }

  public void testCustomComponentFields() {
    doTestNavigation("CustomVBox", "tf", "custom/" + getTestName(true) + ".fxml", "custom/CustomVBox.java");
  }

  public void testCustomComponentFieldsWithSameProperties() {
    doTest("custom/" + getTestName(true) + ".fxml", "custom/" + getTestName(false) + ".java");
  }

  public void testCustomComponent_Fields() {
    doTest("custom/" + getTestName(true) + ".fxml", "custom/_CustomVBox.java");
  }

  public void testInjectedController() {
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

  public void testControllerInExpression() {
    doTest(getTestName(true) + ".fxml", getTestName(false) + ".java", getTestName(false) + "Wrapper.java");
  }

  public void testNamedColor() {
    doTestNavigation(JavaFxCommonNames.JAVAFX_SCENE_COLOR, "ORANGE");
  }

  private void doTestNavigation(String resultClassName, String resultFieldName) {
    doTestNavigation(resultClassName, resultFieldName, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void doTestNavigation(String resultClassName, String resultFieldName, String... additionalPaths) {
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

  public void testNavigationFromMainToFxml() {
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

  public void testSourceAttrRecognition() {
    doTest();
  }

  public void testReferenceAttributes() {
    doTest();
  }

  public void testVariables() {
    doTest();
  }

  public void testDefineAttributes() {
    doTest();
  }

  public void testDefinedElements() {
    doTest();
  }

  public void testPropertyElementsWithAnyAttributes() {
    doTest();
  }

  public void testHandlerWithoutController() {
    doTest();
  }

  public void testHandlerWithoutPageLanguage() {
    doTest();
  }

  public void testIdOutOfHierarchy() {
    doTest("btn.fxml", "MyController.java");
  }

  public void testIncludeBtn() {
    doTest("btn.fxml");
  }

  public void testWrongBindingType() {
    doTest(getTestName(false) + ".java");
  }

  public void testAllowIncludeTagInsideDefine() {
    doTest("btn.fxml");
  }

  public void testValueOfAcceptance() {
    doTest();
  }

  public void testInstantiationAcceptance() {
    doTest();
  }

  public void testInstantiationAcceptanceWithNameArg() {
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

  public void testFqnTagNames() {
    doTest();
  }

  public void testRootTag() {
    doTest();
  }

  public void testUnresolvedRootTag() {
    doTest();
  }

  public void testRootTagWithoutType() {
    doTest();
  }

  public void testRootTagWithImport() {
    doTest();
  }

  public void testReadOnly() {
    doTest();
  }

  public void testReadOnly1() {
    doTest();
  }

  public void testScriptSource() {
    doTest("s1.js");
  }

  public void testPropertyNameExpression() {
    doTest();
  }

  public void testPropertyChainExpression() {
    doTest();
  }

  public void testIncorrectPropertyExpressionSyntax() {
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

  public void testExpressionBinding() {
    doTest();
  }

  public void testPropertyWithoutField() {
    doTest();
  }

  public void testRootTagProperties() {
    doTest();
  }

  public void testBooleanPropertyWithoutField() {
    doTest();
  }

  public void testShortNamesInRootType() {
    doTest();
  }

  public void testLineChartInstantiation() {
    doTest();
  }

  public void testUnexpectedNode() {
    doTest();
  }

  public void testDefaultPropertyField() {
    doTest();
  }

  public void testPrimitiveSubtags() {
    doTest();
  }

  public void testReferencePosition() {
    doTest();
  }

  public void testAcceptReferenceInsideDefine() {
    doTest();
  }

  public void testRootTagOnDifferentLevels() {
    doTest();
  }

  public void testAbsenceOfDefineAttributes() {
    doTest();
  }

  public void testCopyReference() {
    doTest();
  }

  public void testConstantValue() {
    doTest();
  }

  public void testEnumConstantValue() {
    doTest();
  }

  public void testNestedClassConstants() {
    doTest("model/" + getTestName(false) + "Model.java");
  }

  public void testBoxedConstantValue() {
    doTest();
  }

  public void testLiteralValue() {
    doTest();
  }

  public void testNullObjectValue() {
    doTest();
  }

  public void testNullPrimitiveValue() {
    doTest();
  }

  public void testFactoryMethod() {
    doTest();
  }

  public void testPrivateControllerMethod() {
    doTest(getTestName(false) + ".java");
  }

  public void testPropertyTagCompatibleClass() {
    doTest();
  }

  public void testPropertyTagCompatiblePrimitive() {
    doTest();
  }

  public void testPropertyTagIncompatibleClass() {
    doTest();
  }

  public void testPropertyTagIncompatiblePrimitive() {
    doTest();
  }

  public void testPropertyTagUnrelatedClass() {
    doWarningsTest();
  }

  public void testPropertyTagUnrelatedPrimitive() {
    doWarningsTest();
  }

  public void testCharsetInInclude() {
    myFixture.addFileToProject("sample.fxml", "<?import javafx.scene.layout.GridPane?>\n" +
                                                 "<fx:root type=\"javafx.scene.layout.GridPane\" xmlns:fx=\"http://javafx.com/fxml\"/>\n");
    doWarningsTest();
  }

  public void testIncludedForm() {
    myFixture.addFileToProject("sample.fxml", "<?import javafx.scene.layout.GridPane?>\n" +
                                              "<fx:root type=\"javafx.scene.layout.GridPane\" xmlns:fx=\"http://javafx.com/fxml\"/>\n");
    doWarningsTest();
  }
  
  public void testInjectedControllerFields() {
    myFixture.addFileToProject("sample.fxml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                              "<?import javafx.scene.control.*?>\n" +
                                              "<?import javafx.scene.layout.*?>\n" +
                                              "<AnchorPane id=\"AnchorPane\" xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + getTestName(false)+ "\">\n" +
                                              "  <Button fx:id=\"id1\" />\n" +
                                              "</AnchorPane>\n");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testAbsoluteRemoteUrl() {
    doTest();
  }

  public void testMultipleStylesheetsAttribute() {
    myFixture.addFileToProject("mystyle.css", ".myStyle {}");
    myFixture.addFileToProject("very/deeply/located/small.css", ".small {}");
    doTest();
  }

  public void testMultipleStylesheetsTag() {
    myFixture.addFileToProject("mystyle.css", ".myStyle {}");
    myFixture.addFileToProject("very/deeply/located/small.css", ".small {}");
    doTest();
  }

  public void testPrivateEventHandler() {
    myFixture.configureByFiles(getTestName(true) + ".fxml", getTestName(false) + ".java");
    myFixture.testHighlighting(true, true, true, getTestName(false) + ".java");
  }

  public void testFxIdInSuperclass() {
    doTestControllerSuperclass();
  }

  public void testEventHandlerInSuperclass() {
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

  public void testResourceKeyInAttribute() {
    myFixture.addFileToProject("messages.properties", "string.key=My text\n" +
                                                      "double.key=123.456\n");
    doTest();
  }

  public void testFxIdUsedInSameNode() {
    myFixture.configureByFiles(getTestName(true) + ".fxml", getTestName(false) + ".java");
    doTest();
  }

  public void testConstructorNamedArg() {
    doTest();
  }

  public void testBuiltInFxmlFieldsAndMethod() {
    myFixture.addClass("package java.util; public class ResourceBundle {}");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java", getTestName(true) + ".fxml");
  }

  private void doTest() {
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
