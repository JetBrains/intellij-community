// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor;
import org.jetbrains.plugins.javaFX.refactoring.JavaFxRenameAttributeProcessor;

import java.util.LinkedHashMap;
import java.util.Map;

public class JavaFxHeadlessRenameTest extends AbstractJavaFXTestCase {
  public void testFxIdRenamesTheControllerField() {
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
    XmlAttributeValue fxId = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), XmlAttributeValue.class);
    assertNotNull(fxId);
    HeadlessRenamePsiElementProcessor processor = (HeadlessRenamePsiElementProcessor)HeadlessRenamePsiElementProcessor.processorOf(fxId);
    assertInstanceOf(processor, JavaFxRenameAttributeProcessor.class);

    Map<PsiElement, String> allRenames = new LinkedHashMap<>();
    processor.prepareRenamingHeadless(fxId, "secret", allRenames);

    assertEquals("secret", allRenames.get(controller.findFieldByName("passwordField", false)));
  }
}
