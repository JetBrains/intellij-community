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

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: 1/17/13
 */
public class JavaFxCompletionTest extends LightFixtureCompletionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return AbstractJavaFXTestCase.JAVA_FX_DESCRIPTOR;
  }

  public void testAvailablePositions() throws Exception {
    doTest();
  }

  public void testStaticProperties() throws Exception {
    doTest("GridPane.columnIndex");
  }
  
  public void testStaticPropertiesTag() throws Exception {
    doTest("GridPane.columnIndex");
  }

  public void testSimplePropertyTag() throws Exception {
    doTest("text");
  }

  public void testListPropertyTag() throws Exception {
    doTest("children");
  }

  public void testClassInsideList() throws Exception {
    doTest("Button");
  }

  public void testClassDefaultProperty() throws Exception {
    doTest("Button");
  }

  public void testClassInsertImport() throws Exception {
    doTest("Button");
  }

  public void testDefaultProperty() throws Exception {
    doTest("fx:id");
  }

  public void testDefaultTag() throws Exception {
    doTest("fx:script");
  }

  public void testStaticPropertiesEnumValue() throws Exception {
    doTest();
  }

  public void testEnumConstantValue() throws Exception {
    doTest("TOP_LEFT");
  }

  public void testConstants() throws Exception {
    doTest("NEGATIVE_INFINITY");
  }

  public void testReferencedAttributes() throws Exception {
    doTest("text");
  }

  public void testFactoryMethods() throws Exception {
    doTest("observableArrayList");
  }

  public void testVariableCompletion() throws Exception {
    doTest();
  }

  public void testExpressionBinding() throws Exception {
    doTest("text");
  }

  public void testStylesheets() throws Exception {
    doTest("mystyle.css", "mystyle.css");
  }

  public void testStylesheetsStringValue() throws Exception {
    doTest("mystyle.css", "mystyle.css");
  }

  public void testListAttributes() throws Exception {
    doTest("stylesheets");
  }

  public void testNamedColors() throws Exception {
    doTest("blue");
  }

  public void testRootTagNameLayout() throws Exception {
    doTest("GridPane");
  }

  public void testChildrenInsideGridPaneRoot() throws Exception {
    doTest("children");
  }

  public void testClassInsideObjectProperty() throws Exception {
    doTest("Insets");
  }

  public void testPrimitiveProperties() throws Exception {
    doTest("top");
  }

  public void testPrimitiveSubtags() throws Exception {
    configureAndComplete();
    assertDoesntContain(myFixture.getLookupElementStrings(), "geomBoundsInvalid");
  }

  public void testDefaultPropertyWrappedField() throws Exception {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "image", "Image");
  }

  public void testInfinity() throws Exception {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "Infinity", "-Infinity", "NaN", "-NaN");
  }

  public void testNoInfinity() throws Exception {
    configureAndComplete();
    assertDoesntContain(myFixture.getLookupElementStrings(), "Infinity");
  }

  public void testBooleanValues() throws Exception {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "true", "false");
  }

  public void testBooleanValuesNonStatic() throws Exception {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "true", "false");
  }

  public void testPropertyNameWithoutField() throws Exception {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "disable");
  }

  public void testPropertyTagSubclass() throws Exception {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "Color", "ImagePattern", "LinearGradient", "RadialGradient");
    assertDoesntContain(myFixture.getLookupElementStrings(), "Paint");
  }

  public void testNullFxId() throws Exception {
    configureAndComplete();
    assertDoesntContain(myFixture.getLookupElementStrings(), "null");
  }

  public void testSubclassesAndDefaultProperty() throws Exception {
    configureAndComplete();
    final List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    final String buttonVariant = "Button";
    assertContainsElements(lookupElementStrings, buttonVariant);
    assertTrue(lookupElementStrings.toString(), lookupElementStrings.lastIndexOf(buttonVariant) == lookupElementStrings.indexOf(buttonVariant));
  }

  public void testDefaultPropertyIncludeOnce() throws Exception {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    myItems = myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "fx:reference");
    assertEquals(3, myItems.length);
  }

  public void testAcceptableSourceOnly() throws Exception {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    myItems = myFixture.completeBasic();
    assertEmpty(myItems);
  }

  public void testIncludedRootAttributes() throws Exception {
    myFixture.addFileToProject("foo.fxml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                   "<?import javafx.scene.layout.*?>\n" +
                                   "<VBox xmlns:fx=\"http://javafx.com/fxml\"/>");
    doTest("layoutY");
  }

  public void testIncludedRootRootAttributes() throws Exception {
    myFixture.addFileToProject("sample.fxml", "?import javafx.scene.layout.GridPane?>\n" +
                                              "<fx:root type=\"javafx.scene.layout.GridPane\" xmlns:fx=\"http://javafx.com/fxml\" />");
    doTest("blendMode");
  }

  public void testAllowPropertyTypeClass() throws Exception {
    doTest("ColumnConstraints");
  }

  public void testEventHandlerMethod() throws Exception {
    configureAndComplete(getTestName(false) + ".java", getTestName(false) + "Super.java");
    assertSameElements(myFixture.getLookupElementStrings(), "onMyKeyTyped", "onSuperKeyTyped");
  }

  public void testEventHandlerMethodTypeParam() throws Exception {
    configureAndComplete(getTestName(false) + ".java", getTestName(false) + "Super.java");
    assertSameElements(myFixture.getLookupElementStrings(), "onMyKeyTyped", "onSuperKeyTyped");
  }

  public void testRawCollectionItem() throws Exception {
    configureAndComplete();
    assertDoesntContain(myFixture.getLookupElementStrings(), "T", "Object", "java.lang.Object");
  }

  public void testFxIdExactOptionsLabel() throws Exception {
    configureAndComplete("FxIdExactOptionsController.java", "FxIdExactOptionsModel.java");
    assertSameElements(myFixture.getLookupElementStrings(), "parentPrivateLabel", "parentPublicLabel", "privateLabel", "publicLabel", "parentControl", "control", "grandLabel");
  }

  public void testFxIdExactOptionsDefine() throws Exception {
    configureAndComplete("FxIdExactOptionsController.java", "FxIdExactOptionsModel.java");
    assertSameElements(myFixture.getLookupElementStrings(), "parentModel", "model");
  }

  public void testFxIdGuessedOptionsRoot() throws Exception {
    configureAndComplete("FxIdGuessedOptionsController.java");
    assertSameElements(myFixture.getLookupElementStrings(), "pane", "box", "model");
  }

  public void testFxIdGuessedOptionsNode() throws Exception {
    configureAndComplete("FxIdGuessedOptionsController.java");
    assertSameElements(myFixture.getLookupElementStrings(), "pane", "node", "box", "model");
  }

  public void testFxIdGuessedOptionsDefine() throws Exception {
    configureAndComplete("FxIdGuessedOptionsController.java");
    assertSameElements(myFixture.getLookupElementStrings(), "pane", "node", "box", "model", "text", "target");
  }

  public void testFxIdGuessedOptionsNested() throws Exception {
    configureAndComplete("FxIdGuessedOptionsController.java");
    assertSameElements(myFixture.getLookupElementStrings(),"pane", "node", "box", "model", "text", "target");
  }

  public void testInheritedConstant() throws Exception {
    configureAndComplete("InheritedConstantData.java", "InheritedConstantSuperData.java");
    assertSameElements(myFixture.getLookupElementStrings(), "MY_TEXT", "SUPER_TEXT");
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

  private void configureAndComplete(final String... extraFiles) {
    final String fxmlFileName = getTestName(true) + ".fxml";
    if (extraFiles.length != 0) {
      final List<String> files = new ArrayList<>();
      files.add(fxmlFileName);
      Collections.addAll(files, extraFiles);
      myFixture.configureByFiles(ArrayUtil.toStringArray(files));
    }
    else {
      myFixture.configureByFiles(fxmlFileName);
    }
    complete();
  }

  public void testOnlyCssAsStylesheets() throws Exception {
    myFixture.addFileToProject("my.fxml", "");
    myFixture.addFileToProject("my.png", "");
    myFixture.addFileToProject("sample.css", ".root{}");
    configureByFile(getTestName(true) + ".fxml");
    assertTrue(myItems.length == 1);
    LookupElement selectionElement = null;
    for (LookupElement item : myItems) {
      if (item.getLookupString().equals("sample.css")) {
        selectionElement = item;
        break;
      }
    }
    if (selectionElement == null) {
      fail("sample.css was not found");
    }
  }

  public void testReadOnly() throws Exception {
    configureByFile(getTestName(true) + ".fxml");
    assertTrue(myItems.length > 0);
    LookupElement selectionElement = null;
    for (LookupElement item : myItems) {
      if (item.getLookupString().equals("backgroundFills")) {
        selectionElement = item;
        break;
      }
    }
    if (selectionElement != null) {
      fail("Read only attribute was suggested");
    }
  }

  public void testResourceProperty() {
    myFixture.addFileToProject("messages.properties", "double.key=123.456\n" +
                                                      "string.key=Some text\n");
    configureAndComplete();
    assertSameElements(myFixture.getLookupElementStrings(), "double.key", "string.key");
  }


  public void testResourcePropertyManyFiles() {
    myFixture.addFileToProject("messages1.properties", "double.key=123.456\n");
    myFixture.addFileToProject("messages2.properties", "string.key=Some text\n");
    configureAndComplete();
    assertSameElements(myFixture.getLookupElementStrings(), "double.key", "string.key");
  }

  private void doTest() throws Exception {
    doTest(null);
  }

  private void doTest(final String selection) throws Exception {
    doTest(selection, null);
  }

  private void doTest(final String selection, String additionalPath) throws Exception {
    final String mainFxml = getTestName(true) + ".fxml";
    if (additionalPath != null) {
      myFixture.configureByFiles(mainFxml, additionalPath);
      complete();
    } else {
      configureByFile(mainFxml);
    }
    assertTrue(myItems.length > 0);
    LookupElement selectionElement = null;
    for (LookupElement item : myItems) {
      if (item.getLookupString().equals(selection)) {
        selectionElement = item;
        break;
      }
    }
    if (selection != null && selectionElement == null) {
      fail(selection + " is not suggested");
    }
    if (selectionElement == null) {
      selectionElement = myItems[0];
    }
    selectItem(selectionElement);
    checkResultByFile(getTestName(true) + "_after.fxml");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/completion/";
  }
}
