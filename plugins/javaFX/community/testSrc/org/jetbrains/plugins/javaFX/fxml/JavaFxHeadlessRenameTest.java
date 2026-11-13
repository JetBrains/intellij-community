// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.javaFX.refactoring.JavaFxRenameAttributeProcessor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaFxHeadlessRenameTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx = javaFxFixture(projectFixture);

  @Test
  public void testFxIdRenamesTheControllerField() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    PsiClass controller = myFixture.addClass("""
                                               public class ExampleController {
                                                 public javafx.scene.control.PasswordField passwordField;
                                               }""");
    myFixture.configureByText("sample.fxml", """
      <?import javafx.scene.control.*?>
      <?import javafx.scene.layout.*?>
      <AnchorPane xmlns:fx="http://javafx.com/fxml" fx:controller="ExampleController">
        <PasswordField fx:id="pass<caret>wordField"/>
      </AnchorPane>""");
    ReadAction.runBlocking(() -> {
      XmlAttributeValue fxId = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), XmlAttributeValue.class);
      assertNotNull(fxId);
      HeadlessRenamePsiElementProcessor processor = (HeadlessRenamePsiElementProcessor)HeadlessRenamePsiElementProcessor.processorOf(fxId);
      assertInstanceOf(JavaFxRenameAttributeProcessor.class, processor);

      Map<PsiElement, String> allRenames = new LinkedHashMap<>();
      processor.prepareRenamingHeadless(fxId, "secret", allRenames);

      assertEquals("secret", allRenames.get(controller.findFieldByName("passwordField", false)));
    });
  }
}
