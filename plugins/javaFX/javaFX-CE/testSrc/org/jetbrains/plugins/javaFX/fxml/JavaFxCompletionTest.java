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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaFxCompletionTest extends LightFixtureCompletionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return AbstractJavaFXTestCase.JAVA_FX_DESCRIPTOR;
  }

  public void testAvailablePositions() throws Exception {
    doTest();
  }

  public void testStaticProperties() {
    doTest("GridPane.columnIndex");
  }
  
  public void testStaticPropertiesTag() {
    doTest("GridPane.columnIndex");
  }

  public void testSimplePropertyTag() {
    doTest("text");
  }

  public void testListPropertyTag() {
    doTest("children");
  }

  public void testClassInsideList() {
    doTest("Button");
  }

  public void testClassDefaultProperty() {
    doTest("Button");
  }

  public void testClassInsertImport() {
    doTest("Button");
  }

  public void testDefaultProperty() {
    doTest("fx:id");
  }

  public void testDefaultTag() {
    doTest("fx:script");
  }

  public void testStaticPropertiesEnumValue() throws Exception {
    doTest();
  }

  public void testEnumConstantValue() {
    doTest("TOP_LEFT");
  }

  public void testConstants() {
    doTest("NEGATIVE_INFINITY");
  }

  public void testReferencedAttributes() {
    doTest("text");
  }

  public void testFactoryMethods() {
    doTest("observableArrayList");
  }

  public void testVariableCompletion() throws Exception {
    doTest();
  }

  public void testExpressionBinding() {
    doTest("text");
  }

  public void testStylesheets() {
    doTest("mystyle.css", "mystyle.css");
  }

  public void testStylesheetsStringValue() {
    doTest("mystyle.css", "mystyle.css");
  }

  public void testListAttributes() {
    doTest("stylesheets");
  }

  public void testNamedColors() {
    doTest("blue");
  }

  public void testRootTagNameLayout() {
    doTest("GridPane");
  }

  public void testChildrenInsideGridPaneRoot() {
    doTest("children");
  }

  public void testClassInsideObjectProperty() {
    doTest("Insets");
  }

  public void testPrimitiveProperties() {
    doTest("top");
  }

  public void testPrimitiveSubtags() {
    configureAndComplete();
    assertDoesntContain(myFixture.getLookupElementStrings(), "geomBoundsInvalid");
  }

  public void testDefaultPropertyWrappedField() {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "image", "Image");
  }

  public void testInfinity() {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "Infinity", "-Infinity", "NaN", "-NaN");
  }

  public void testNoInfinity() {
    configureAndComplete();
    assertDoesntContain(myFixture.getLookupElementStrings(), "Infinity");
  }

  public void testBooleanValues() {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "true", "false");
  }

  public void testBooleanValuesNonStatic() {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "true", "false");
  }

  public void testPropertyNameWithoutField() {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "disable");
  }

  public void testPropertyTagSubclass() {
    configureAndComplete();
    assertContainsElements(myFixture.getLookupElementStrings(), "Color", "ImagePattern", "LinearGradient", "RadialGradient");
    assertDoesntContain(myFixture.getLookupElementStrings(), "Paint");
  }

  public void testNullFxId() {
    configureAndComplete();
    assertDoesntContain(myFixture.getLookupElementStrings(), "null");
  }

  public void testSubclassesAndDefaultProperty() {
    configureAndComplete();
    final List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    final String buttonVariant = "Button";
    assertContainsElements(lookupElementStrings, buttonVariant);
    assertTrue(lookupElementStrings.toString(), lookupElementStrings.lastIndexOf(buttonVariant) == lookupElementStrings.indexOf(buttonVariant));
  }

  public void testDefaultPropertyIncludeOnce() {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    myItems = myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "fx:reference");
    assertEquals(3, myItems.length);
  }

  public void testAcceptableSourceOnly() {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    myItems = myFixture.completeBasic();
    assertEmpty(myItems);
  }

  public void testIncludedRootAttributes() {
    myFixture.addFileToProject("foo.fxml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                   "<?import javafx.scene.layout.*?>\n" +
                                   "<VBox xmlns:fx=\"http://javafx.com/fxml\"/>");
    doTest("layoutY");
  }

  public void testIncludedRootRootAttributes() {
    myFixture.addFileToProject("sample.fxml", "?import javafx.scene.layout.GridPane?>\n" +
                                              "<fx:root type=\"javafx.scene.layout.GridPane\" xmlns:fx=\"http://javafx.com/fxml\" />");
    doTest("blendMode");
  }

  public void testAllowPropertyTypeClass() {
    doTest("ColumnConstraints");
  }

  public void testEventHandlerMethod() {
    configureAndComplete(getTestName(false) + ".java", getTestName(false) + "Super.java");
    assertSameElements(myFixture.getLookupElementStrings(), "onMyKeyTyped", "onSuperKeyTyped");
  }

  public void testEventHandlerMethodTypeParam() {
    configureAndComplete(getTestName(false) + ".java", getTestName(false) + "Super.java");
    assertSameElements(myFixture.getLookupElementStrings(), "onMyKeyTyped", "onSuperKeyTyped");
  }

  public void testRawCollectionItem() {
    configureAndComplete();
    assertDoesntContain(myFixture.getLookupElementStrings(), "T", "Object", "java.lang.Object");
  }

  public void testFxIdExactOptionsLabel() {
    configureAndComplete("FxIdExactOptionsController.java", "FxIdExactOptionsModel.java");
    assertSameElements(myFixture.getLookupElementStrings(), "parentPrivateLabel", "parentPublicLabel", "privateLabel", "publicLabel", "parentControl", "control", "grandLabel");
  }

  public void testFxIdExactOptionsDefine() {
    configureAndComplete("FxIdExactOptionsController.java", "FxIdExactOptionsModel.java");
    assertSameElements(myFixture.getLookupElementStrings(), "parentModel", "model");
  }

  public void testFxIdGuessedOptionsRoot() {
    configureAndComplete("FxIdGuessedOptionsController.java");
    assertSameElements(myFixture.getLookupElementStrings(), "pane", "box", "model");
  }

  public void testFxIdGuessedOptionsNode() {
    configureAndComplete("FxIdGuessedOptionsController.java");
    assertSameElements(myFixture.getLookupElementStrings(), "pane", "node", "box", "model");
  }

  public void testFxIdGuessedOptionsDefine() {
    configureAndComplete("FxIdGuessedOptionsController.java");
    assertSameElements(myFixture.getLookupElementStrings(), "pane", "node", "box", "model", "text", "target");
  }

  public void testFxIdGuessedOptionsNested() {
    configureAndComplete("FxIdGuessedOptionsController.java");
    assertSameElements(myFixture.getLookupElementStrings(),"pane", "node", "box", "model", "text", "target");
  }

  public void testInheritedConstant() {
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

  public void testOnlyCssAsStylesheets() {
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

  public void testReadOnly() {
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

  private void doTest() {
    doTest(null);
  }

  private void doTest(final String selection) {
    doTest(selection, null);
  }

  private void doTest(final String selection, String additionalPath) {
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
