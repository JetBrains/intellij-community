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
import java.util.stream.Collectors;

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
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    complete();
    assertDoesntContain(myFixture.getLookupElementStrings(), "geomBoundsInvalid");
  }

  public void testDefaultPropertyWrappedField() throws Exception {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    complete();
    assertContainsElements(myFixture.getLookupElementStrings(), "image", "Image");
  }

  public void testInfinity() throws Exception {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    complete();
    assertContainsElements(myFixture.getLookupElementStrings(), "Infinity", "-Infinity", "NaN", "-NaN");
  }

  public void testNoInfinity() throws Exception {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    complete();
    assertDoesntContain(myFixture.getLookupElementStrings(), "Infinity");
  }

  public void testBooleanValues() throws Exception {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    complete();
    assertContainsElements(myFixture.getLookupElementStrings(), "true", "false");
  }

  public void testBooleanValuesNonStatic() throws Exception {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    complete();
    assertContainsElements(myFixture.getLookupElementStrings(), "true", "false");
  }

  public void testPropertyNameWithoutField() throws Exception {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    complete();
    assertContainsElements(myFixture.getLookupElementStrings(), "disable");
  }

  public void testSubclassesAndDefaultProperty() throws Exception {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    complete();
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

  public void testFxIdExactOptionsLabel() throws Exception {
    doOptionsTest(Arrays.asList("parentPrivateLabel", "parentPublicLabel", "privateLabel", "publicLabel", "parentControl", "control", "grandLabel"),
                  "FxIdExactOptionsController", "FxIdExactOptionsModel");
  }

  public void testFxIdExactOptionsDefine() throws Exception {
    doOptionsTest(Arrays.asList("parentModel", "model"), "FxIdExactOptionsController", "FxIdExactOptionsModel");
  }

  public void testFxIdGuessedOptionsRoot() throws Exception {
    doOptionsTest(Arrays.asList("pane", "box", "model"), "FxIdGuessedOptionsController");
  }

  public void testFxIdGuessedOptionsNode() throws Exception {
    doOptionsTest(Arrays.asList("pane", "node", "box", "model"), "FxIdGuessedOptionsController");
  }

  public void testFxIdGuessedOptionsDefine() throws Exception {
    doOptionsTest(Arrays.asList("pane", "node", "box", "model", "text", "target"), "FxIdGuessedOptionsController");
  }

  public void testFxIdGuessedOptionsNested() throws Exception {
    doOptionsTest(Arrays.asList("pane", "node", "box", "model", "text", "target"), "FxIdGuessedOptionsController");
  }

  private void doOptionsTest(final List<String> expectedOptions, final String... javaClasses) {
    final List<String> files = new ArrayList<>();
    files.add(getTestName(true) + ".fxml");
    Arrays.stream(javaClasses).map(name -> name + ".java").forEach(files::add);
    myFixture.configureByFiles(ArrayUtil.toStringArray(files));
    complete();

    final Set<String> actualOptions = Arrays.stream(myItems).map(LookupElement::getLookupString).collect(Collectors.toSet());
    assertEquals(new HashSet<>(expectedOptions), actualOptions);
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
