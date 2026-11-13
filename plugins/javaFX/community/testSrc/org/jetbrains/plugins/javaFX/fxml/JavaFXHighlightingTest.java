// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author anna
 */
@TestDataPath("$PROJECT_ROOT/community/plugins/javaFX/testData/highlighting")
@TestApplication
public class JavaFXHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx =
    javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/highlighting/",
                  () -> List.of(new XmlPathReferenceInspection(),
                                new RequiredAttributesInspection(),
                                new UnusedDeclarationInspectionBase(true)));

  @Test
  public void testLoginForm() {
    doTest();
  }

  @Test
  public void testUnresolvedTopLevelController() {
    doTest();
  }

  @Test
  public void testController() {
    doTest();
  }

  @Test
  public void testCoercedTypes() {
    doTest();
  }

  @Test
  public void testRootTagCoercedUnchecked() {
    doTest();
  }

  @Test
  public void testStaticProperties() {
    doTest();
  }

  @Test
  public void testStaticPropertiesCustomLayout() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.addClass("""
                         import javafx.scene.layout.GridPane;
                         public class MyGridPane extends GridPane {}
                         """);
    myFixture.testHighlighting(false, false, false, myFixture.getTestName(true) + ".fxml");
  }

  @Test
  public void testEnumValues() {
    doTest();
  }

  @Test
  public void testDefaultTagProperties() {
    doTest();
  }

  @Test
  public void testDefaultTagInList() {
    doTest();
  }

  @Test
  public void testUnresolvedImport() {
    doTest();
  }

  @Test
  public void testImageIcon() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.copyFileToProject("appIcon.png");
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml");
    myFixture.testHighlighting(false, false, false, myFixture.getTestName(true) + ".fxml");
  }

  @Test
  public void testControllerIdRef() {
    doTestIdController();
  }

  @Test
  public void testEventHandlers() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.testHighlighting(false, false, false, myFixture.getTestName(true) + ".fxml");
  }

  @Test
  public void testPackageLocalController() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    doTest(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testNoParamsHandler() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    doTest(myFixture.getTestName(false) + ".java");
  }

  private void doTestIdController() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    final String controllerClassName = myFixture.getTestName(false) + "Controller";
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", controllerClassName + ".java");
    ReadAction.runBlocking(() -> {
      final PsiClass controllerClass = myFixture.findClass(controllerClassName);
      assertNotNull(controllerClass);
      assertTrue(controllerClass.getFields().length > 0);
      final int offset = myFixture.getCaretOffset();
      final PsiReference reference = myFixture.getFile().findReferenceAt(offset);
      assertNotNull(reference);
      assertEquals(controllerClass.getFields()[0], reference.resolve());
    });
  }

  @Test
  public void testConstantNavigation() {
    doTestNavigation("java.lang.Double", "NEGATIVE_INFINITY");
  }

  @Test
  public void testEnumNavigation() {
    doTestNavigation("javafx.geometry.Pos", "CENTER");
  }

  @Test
  public void testCustomComponentFields() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    doTestNavigation("CustomVBox", "tf", "custom/" + myFixture.getTestName(true) + ".fxml", "custom/CustomVBox.java");
  }

  @Test
  public void testCustomComponentFieldsWithSameProperties() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    doTest("custom/" + myFixture.getTestName(true) + ".fxml", "custom/" + myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testCustomComponent_Fields() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    doTest("custom/" + myFixture.getTestName(true) + ".fxml", "custom/_CustomVBox.java");
  }

  @Test
  public void testInjectedController() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.copyFileToProject("injected/MyController.java");
    myFixture.copyFileToProject("injected/FooVBox.java");

    final RegistryValue registryValue = Registry.get("javafx.fxml.controller.from.loader");
    final boolean injectionAllowed = registryValue.asBoolean();
    try {
      registryValue.setValue(true);
      doTestNavigation("injected.MyController", "label", "injected/" + myFixture.getTestName(true) + ".fxml");
    }
    finally {
      registryValue.setValue(injectionAllowed);
    }
  }

  @Test
  public void testControllerInExpression() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    doTest(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "Wrapper.java");
  }

  @Test
  public void testNamedColor() {
    doTestNavigation(JavaFxCommonNames.JAVAFX_SCENE_COLOR, "ORANGE");
  }

  private void doTestNavigation(String resultClassName, String resultFieldName) {
    doTestNavigation(resultClassName, resultFieldName, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  private void doTestNavigation(String resultClassName, String resultFieldName, String... additionalPaths) {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    if (additionalPaths.length == 0) {
      myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml");
    } else {
      myFixture.configureByFiles(additionalPaths);
    }
    ReadAction.runBlocking(() -> {
      final int offset = myFixture.getCaretOffset();
      final PsiReference reference = myFixture.getFile().findReferenceAt(offset);
      assertNotNull(reference);
      final PsiClass resultClass = myFixture.getJavaFacade().findClass(resultClassName, GlobalSearchScope.allScope(myFixture.getProject()));
      assertNotNull(resultClass, "Class " + resultClassName + " not found");
      final PsiField resultField = resultClass.findFieldByName(resultFieldName, false);
      assertNotNull(resultField);
      assertEquals(resultField, reference.resolve());
    });
  }

  @Test
  public void testNavigationFromMainToFxml() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(false) + ".java", myFixture.getTestName(true) + ".fxml");
    ReadAction.runBlocking(() -> {
      final int offset = myFixture.getCaretOffset();
      final PsiReference reference = myFixture.getFile().findReferenceAt(offset);
      assertNotNull(reference);
      final PsiElement resolve = reference.resolve();
      assertNotNull(resolve);
      final PsiFile containingFile = resolve.getContainingFile();
      assertNotNull(containingFile);
      assertEquals(myFixture.getTestName(true) + ".fxml", containingFile.getName());
    });
  }

  @Test
  public void testSourceAttrRecognition() {
    doTest();
  }

  @Test
  public void testReferenceAttributes() {
    doTest();
  }

  @Test
  public void testVariables() {
    doTest();
  }

  @Test
  public void testDefineAttributes() {
    doTest();
  }

  @Test
  public void testDefinedElements() {
    doTest();
  }

  @Test
  public void testPropertyElementsWithAnyAttributes() {
    doTest();
  }

  @Test
  public void testHandlerWithoutController() {
    doTest();
  }

  @Test
  public void testHandlerWithoutPageLanguage() {
    doTest();
  }

  @Test
  public void testIdOutOfHierarchy() {
    doTest("btn.fxml", "MyController.java");
  }

  @Test
  public void testIncludeBtn() {
    doTest("btn.fxml");
  }

  @Test
  public void testWrongBindingType() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    doTest(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testAllowIncludeTagInsideDefine() {
    doTest("btn.fxml");
  }

  @Test
  public void testValueOfAcceptance() {
    doTest();
  }

  @Test
  public void testInstantiationAcceptance() {
    doTest();
  }

  @Test
  public void testInstantiationAcceptanceWithNameArg() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.addClass("""
                         package p;
                         public class Root extends javafx.scene.layout.GridPane{
                           public Root(@javafx.beans.NamedArg("axis") javafx.scene.Node node ) {
                             super(node);
                           }
                           public javafx.beans.property.Property<javafx.scene.Node> axis;  public void setAxis() {}}\s""");
    doTest(myFixture.getTestName(true) + ".fxml");
  }

  @Test
  public void testFqnTagNames() {
    doTest();
  }

  @Test
  public void testRootTag() {
    doTest();
  }

  @Test
  public void testUnresolvedRootTag() {
    doTest();
  }

  @Test
  public void testRootTagWithoutType() {
    doTest();
  }

  @Test
  public void testRootTagWithImport() {
    doTest();
  }

  @Test
  public void testReadOnly() {
    doTest();
  }

  @Test
  public void testReadOnly1() {
    doTest();
  }

  @Test
  public void testScriptSource() {
    doTest("s1.js");
  }

  @Test
  public void testPropertyNameExpression() {
    doTest();
  }

  @Test
  public void testPropertyChainExpression() {
    doTest();
  }

  @Test
  public void testIncorrectPropertyExpressionSyntax() {
    doTest();
  }

  private void doTest(String additionalPath) {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", additionalPath);
    myFixture.testHighlighting(false, false, false, myFixture.getTestName(true) + ".fxml");
  }

  private void doTest(String... paths) {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(paths);
    myFixture.testHighlighting(false, false, false, paths[0]);
  }

  @Test
  public void testExpressionBinding() {
    doTest();
  }

  @Test
  public void testExpressionBindingArithmetic() {
    doTest();
  }

  @Test
  public void testExpressionBindingComparison() {
    doTest();
  }

  @Test
  public void testExpressionBindingLiterals() {
    doTest();
  }

  @Test
  public void testExpressionBindingMismatchedParens() {
    doTest();
  }

  @Test
  public void testPropertyWithoutField() {
    doTest();
  }

  @Test
  public void testRootTagProperties() {
    doTest();
  }

  @Test
  public void testBooleanPropertyWithoutField() {
    doTest();
  }

  @Test
  public void testShortNamesInRootType() {
    doTest();
  }

  @Test
  public void testLineChartInstantiation() {
    doTest();
  }

  @Test
  public void testUnexpectedNode() {
    doTest();
  }

  @Test
  public void testDefaultPropertyField() {
    doTest();
  }

  @Test
  public void testPrimitiveSubtags() {
    doTest();
  }

  @Test
  public void testReferencePosition() {
    doTest();
  }

  @Test
  public void testAcceptReferenceInsideDefine() {
    doTest();
  }

  @Test
  public void testRootTagOnDifferentLevels() {
    doTest();
  }

  @Test
  public void testAbsenceOfDefineAttributes() {
    doTest();
  }

  @Test
  public void testCopyReference() {
    doTest();
  }

  @Test
  public void testConstantValue() {
    doTest();
  }

  @Test
  public void testEnumConstantValue() {
    doTest();
  }

  @Test
  public void testNestedClassConstants() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    doTest("model/" + myFixture.getTestName(false) + "Model.java");
  }

  @Test
  public void testBoxedConstantValue() {
    doTest();
  }

  @Test
  public void testLiteralValue() {
    doTest();
  }

  @Test
  public void testNullObjectValue() {
    doTest();
  }

  @Test
  public void testNullPrimitiveValue() {
    doTest();
  }

  @Test
  public void testFactoryMethod() {
    doTest();
  }

  @Test
  public void testPrivateControllerMethod() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    doTest(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testPropertyTagCompatibleClass() {
    doTest();
  }

  @Test
  public void testPropertyTagCompatiblePrimitive() {
    doTest();
  }

  @Test
  public void testPropertyTagIncompatibleClass() {
    doTest();
  }

  @Test
  public void testPropertyTagIncompatiblePrimitive() {
    doTest();
  }

  @Test
  public void testPropertyTagUnrelatedClass() {
    doWarningsTest();
  }

  @Test
  public void testPropertyTagUnrelatedPrimitive() {
    doWarningsTest();
  }

  @Test
  public void testCharsetInInclude() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.addFileToProject("sample.fxml", """
      <?import javafx.scene.layout.GridPane?>
      <fx:root type="javafx.scene.layout.GridPane" xmlns:fx="http://javafx.com/fxml"/>
      """);
    doWarningsTest();
  }

  @Test
  public void testIncludedForm() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.addFileToProject("sample.fxml", """
      <?import javafx.scene.layout.GridPane?>
      <fx:root type="javafx.scene.layout.GridPane" xmlns:fx="http://javafx.com/fxml"/>
      """);
    doWarningsTest();
  }

  @Test
  public void testInjectedControllerFields() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.addFileToProject("sample.fxml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                              "<?import javafx.scene.control.*?>\n" +
                                              "<?import javafx.scene.layout.*?>\n" +
                                              "<AnchorPane id=\"AnchorPane\" xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + myFixture.getTestName(false)+ "\">\n" +
                                              "  <Button fx:id=\"id1\" />\n" +
                                              "</AnchorPane>\n");
    myFixture.testHighlighting(true, false, false, myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testAbsoluteRemoteUrl() {
    doTest();
  }

  @Test
  public void testMultipleStylesheetsAttribute() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.addFileToProject("mystyle.css", ".myStyle {}");
    myFixture.addFileToProject("very/deeply/located/small.css", ".small {}");
    doTest();
  }

  @Test
  public void testMultipleStylesheetsTag() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.addFileToProject("mystyle.css", ".myStyle {}");
    myFixture.addFileToProject("very/deeply/located/small.css", ".small {}");
    doTest();
  }

  @Test
  public void testPrivateEventHandler() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(false) + ".java");
    myFixture.testHighlighting(true, true, true, myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testFxIdInSuperclass() {
    doTestControllerSuperclass();
  }

  @Test
  public void testEventHandlerInSuperclass() {
    doTestControllerSuperclass();
  }

  private void doTestControllerSuperclass() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    final String superclass = myFixture.getTestName(false);
    myFixture.copyFileToProject(superclass + ".java");
    myFixture.addClass("public class SubclassingController extends " + superclass + " {}");
    myFixture.addFileToProject("sample.fxml", """
      <?import javafx.scene.layout.VBox?>
      <?import javafx.scene.control.Button?>
      <VBox xmlns:fx="http://javafx.com/fxml/1" xmlns="http://javafx.com/javafx/8"
            fx:controller="SubclassingController">
          <Button fx:id="inheritedButton" onAction="#onAction"/>
      </VBox>""");

    myFixture.testHighlighting(true, true, true, superclass + ".java");
  }

  @Test
  public void testResourceKeyInAttribute() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.addFileToProject("messages.properties", """
      string.key=My text
      double.key=123.456
      """);
    doTest();
  }

  @Test
  public void testFxIdUsedInSameNode() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml", myFixture.getTestName(false) + ".java");
    doTest();
  }

  @Test
  public void testConstructorNamedArg() {
    doTest();
  }

  @Test
  public void testBuiltInFxmlFieldsAndMethod() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.addClass("package java.util; public class ResourceBundle {}");
    myFixture.testHighlighting(true, false, false, myFixture.getTestName(false) + ".java", myFixture.getTestName(true) + ".fxml");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.testHighlighting(false, false, false, myFixture.getTestName(true) + ".fxml");
  }

  private void doWarningsTest() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.testHighlighting(true, false, false, myFixture.getTestName(true) + ".fxml");
  }
}
