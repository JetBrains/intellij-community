// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestDataPath("$CONTENT_ROOT/../../testData/completion")
@TestApplication
public class JavaFxCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JavaFxFixture.JAVA_FX_DESCRIPTOR);
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/completion/");

  @Test
  public void testAvailablePositions() {
    doTest();
  }

  @Test
  public void testStaticProperties() {
    doTest("GridPane.columnIndex");
  }

  @Test
  public void testStaticPropertiesTag() {
    doTest("GridPane.columnIndex");
  }

  @Test
  public void testSimplePropertyTag() {
    doTest("text");
  }

  @Test
  public void testListPropertyTag() {
    doTest("children");
  }

  @Test
  public void testClassInsideList() {
    doTest("Button");
  }

  @Test
  public void testClassDefaultProperty() {
    doTest("Button");
  }

  @Test
  public void testClassInsertImport() {
    doTest("Button");
  }

  @Test
  public void testDefaultProperty() {
    doTest("fx:id");
  }

  @Test
  public void testDefaultTag() {
    doTest("fx:script");
  }

  @Test
  public void testStaticPropertiesEnumValue() {
    doTest();
  }

  @Test
  public void testEnumConstantValue() {
    doTest("TOP_LEFT");
  }

  @Test
  public void testConstants() {
    doTest("NEGATIVE_INFINITY");
  }

  @Test
  public void testReferencedAttributes() {
    doTest("text");
  }

  @Test
  public void testFactoryMethods() {
    doTest("observableArrayList");
  }

  @Test
  public void testVariableCompletion() {
    doTest();
  }

  @Test
  public void testExpressionBinding() {
    doTest("text");
  }

  @Test
  public void testExpressionBindingIncomplete() {
    doTest("text");
  }

  @Test
  public void testExpressionBindingInArithmetic() {
    doTest("text");
  }

  @Test
  public void testExpressionBindingAfterOperator() {
    doTest("text");
  }

  @Test
  public void testStylesheets() {
    doTest("mystyle.css", "mystyle.css");
  }

  @Test
  public void testStylesheetsStringValue() {
    doTest("mystyle.css", "mystyle.css");
  }

  @Test
  public void testListAttributes() {
    doTest("stylesheets");
  }

  @Test
  public void testNamedColors() {
    doTest("blue");
  }

  @Test
  public void testRootTagNameLayout() {
    doTest("GridPane");
  }

  @Test
  public void testChildrenInsideGridPaneRoot() {
    doTest("children");
  }

  @Test
  public void testClassInsideObjectProperty() {
    doTest("Insets");
  }

  @Test
  public void testPrimitiveProperties() {
    doTest("top");
  }

  @Test
  public void testPrimitiveSubtags() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).doesNotContain("geomBoundsInvalid");
    });
  }

  @Test
  public void testDefaultPropertyWrappedField() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).contains("image", "Image");
    });
  }

  @Test
  public void testInfinity() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).contains("Infinity", "-Infinity", "NaN", "-NaN");
    });
  }

  @Test
  public void testNoInfinity() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).doesNotContain("Infinity");
    });
  }

  @Test
  public void testBooleanValues() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).contains("true", "false");
    });
  }

  @Test
  public void testBooleanValuesNonStatic() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).contains("true", "false");
    });
  }

  @Test
  public void testPropertyNameWithoutField() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).contains("disable");
    });
  }

  @Test
  public void testPropertyTagSubclass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).contains("Color", "ImagePattern", "LinearGradient", "RadialGradient");
      assertThat(myFixture.getLookupElementStrings()).doesNotContain("Paint");
    });
  }

  @Test
  public void testNullFxId() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).doesNotContain("null");
    });
  }

  @Test
  public void testSubclassesAndDefaultProperty() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete();
      final List<String> lookupElementStrings = myFixture.getLookupElementStrings();
      assertNotNull(lookupElementStrings);
      final String buttonVariant = "Button";
      assertThat(lookupElementStrings).contains(buttonVariant);
      assertTrue(lookupElementStrings.lastIndexOf(buttonVariant) == lookupElementStrings.indexOf(buttonVariant), lookupElementStrings.toString());
    });
  }

  @Test
  public void testDefaultPropertyIncludeOnce() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml");
      completion.get().setItems(myFixture.completeBasic());
      assertThat(myFixture.getLookupElementStrings()).contains("fx:reference");
      assertEquals(3, completion.get().getItems().length);
    });
  }

  @Test
  public void testAcceptableSourceOnly() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFiles(myFixture.getTestName(true) + ".fxml");
      completion.get().setItems(myFixture.completeBasic());
      assertThat(completion.get().getItems()).isEmpty();
    });
  }

  @Test
  public void testIncludedRootAttributes() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addFileToProject("foo.fxml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <?import javafx.scene.layout.*?>
        <VBox xmlns:fx="http://javafx.com/fxml"/>""");
      doTest("layoutY");
    });
  }

  @Test
  public void testIncludedRootRootAttributes() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addFileToProject("sample.fxml", "?import javafx.scene.layout.GridPane?>\n" +
                                                "<fx:root type=\"javafx.scene.layout.GridPane\" xmlns:fx=\"http://javafx.com/fxml\" />");
      doTest("blendMode");
    });
  }

  @Test
  public void testAllowPropertyTypeClass() {
    doTest("ColumnConstraints");
  }

  @Test
  public void testEventHandlerMethod() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete(myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "Super.java");
      assertThat(myFixture.getLookupElementStrings()).containsOnly("onMyKeyTyped", "onSuperKeyTyped");
    });
  }

  @Test
  public void testEventHandlerMethodTypeParam() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete(myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "Super.java");
      assertThat(myFixture.getLookupElementStrings()).containsOnly("onMyKeyTyped", "onSuperKeyTyped");
    });
  }

  @Test
  public void testRawCollectionItem() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).doesNotContain("T", "Object", "java.lang.Object");
    });
  }

  @Test
  public void testFxIdExactOptionsLabel() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete("FxIdExactOptionsController.java", "FxIdExactOptionsModel.java");
      assertThat(myFixture.getLookupElementStrings()).containsOnly("parentPrivateLabel", "parentPublicLabel", "privateLabel", "publicLabel", "parentControl", "control", "grandLabel");
    });
  }

  @Test
  public void testFxIdExactOptionsDefine() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete("FxIdExactOptionsController.java", "FxIdExactOptionsModel.java");
      assertThat(myFixture.getLookupElementStrings()).containsOnly("parentModel", "model");
    });
  }

  @Test
  public void testFxIdGuessedOptionsRoot() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete("FxIdGuessedOptionsController.java");
      assertThat(myFixture.getLookupElementStrings()).containsOnly("pane", "box", "model");
    });
  }

  @Test
  public void testFxIdGuessedOptionsNode() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete("FxIdGuessedOptionsController.java");
      assertThat(myFixture.getLookupElementStrings()).containsOnly("pane", "node", "box", "model");
    });
  }

  @Test
  public void testFxIdGuessedOptionsDefine() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete("FxIdGuessedOptionsController.java");
      assertThat(myFixture.getLookupElementStrings()).containsOnly("pane", "node", "box", "model", "text", "target");
    });
  }

  @Test
  public void testFxIdGuessedOptionsNested() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete("FxIdGuessedOptionsController.java");
      assertThat(myFixture.getLookupElementStrings()).containsOnly("pane", "node", "box", "model", "text", "target");
    });
  }

  @Test
  public void testInheritedConstant() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureAndComplete("InheritedConstantData.java", "InheritedConstantSuperData.java");
      assertThat(myFixture.getLookupElementStrings()).containsOnly("MY_TEXT", "SUPER_TEXT");
    });
  }

  @Test
  public void testMultipleStylesheetsAttribute() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addFileToProject("mystyle.css", ".myStyle {}");
      myFixture.addFileToProject("very/deeply/located/small.css", ".small {}");
      doTest();
    });
  }

  @Test
  public void testMultipleStylesheetsTag() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addFileToProject("mystyle.css", ".myStyle {}");
      myFixture.addFileToProject("very/deeply/located/small.css", ".small {}");
      doTest();
    });
  }

  private void configureAndComplete(final String... extraFiles) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    final String fxmlFileName = myFixture.getTestName(true) + ".fxml";
    if (extraFiles.length != 0) {
      final List<String> files = new ArrayList<>();
      files.add(fxmlFileName);
      Collections.addAll(files, extraFiles);
      myFixture.configureByFiles(ArrayUtilRt.toStringArray(files));
    }
    else {
      myFixture.configureByFiles(fxmlFileName);
    }
    completion.get().complete();
  }

  @Test
  public void testOnlyCssAsStylesheets() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addFileToProject("my.fxml", "");
      myFixture.addFileToProject("my.png", "");
      myFixture.addFileToProject("sample.css", ".root{}");
      completion.get().configureByFile(myFixture.getTestName(true) + ".fxml");
      assertEquals(1, completion.get().getItems().length);
      LookupElement selectionElement = null;
      for (LookupElement item : completion.get().getItems()) {
        if (item.getLookupString().equals("sample.css")) {
          selectionElement = item;
          break;
        }
      }
      if (selectionElement == null) {
        fail("sample.css was not found");
      }
    });
  }

  @Test
  public void testReadOnly() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(true) + ".fxml");
      assertTrue(completion.get().getItems().length > 0);
      LookupElement selectionElement = null;
      for (LookupElement item : completion.get().getItems()) {
        if (item.getLookupString().equals("backgroundFills")) {
          selectionElement = item;
          break;
        }
      }
      if (selectionElement != null) {
        fail("Read only attribute was suggested");
      }
    });
  }

  @Test
  public void testResourceProperty() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addFileToProject("messages.properties", """
        double.key=123.456
        string.key=Some text
        """);
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).containsOnly("double.key", "string.key");
    });
  }

  @Test
  public void testResourcePropertyManyFiles() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addFileToProject("messages1.properties", "double.key=123.456\n");
      myFixture.addFileToProject("messages2.properties", "string.key=Some text\n");
      configureAndComplete();
      assertThat(myFixture.getLookupElementStrings()).containsOnly("double.key", "string.key");
    });
  }

  private void doTest() {
    doTest(null);
  }

  private void doTest(final String selection) {
    doTest(selection, null);
  }

  private void doTest(final String selection, String additionalPath) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      final String mainFxml = myFixture.getTestName(true) + ".fxml";
      if (additionalPath != null) {
        myFixture.configureByFiles(mainFxml, additionalPath);
        completion.get().complete();
      } else {
        completion.get().configureByFile(mainFxml);
      }
      assertTrue(completion.get().getItems().length > 0);
      LookupElement selectionElement = null;
      for (LookupElement item : completion.get().getItems()) {
        if (item.getLookupString().equals(selection)) {
          selectionElement = item;
          break;
        }
      }
      if (selection != null && selectionElement == null) {
        fail(selection + " is not suggested");
      }
      if (selectionElement == null) {
        selectionElement = completion.get().getItems()[0];
      }
      completion.get().selectItem(selectionElement);
      completion.get().checkResultByFile(myFixture.getTestName(true) + "_after.fxml");
    });
  }
}
