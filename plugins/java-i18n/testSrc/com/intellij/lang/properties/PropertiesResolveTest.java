// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
@PlatformTestCase.WrapInCommand
public class PropertiesResolveTest extends CodeInsightTestCase{
  private static final String BASE_PATH = "testData/resolve/";

  public void testJavaStringLiteral() throws Exception{
    configure("C.java");
    checkNavigation();
  }
  public void testJavaStringLiteralEscaped() throws Exception{
    configure("Escaped.java");
    checkNavigation();
  }

  public void testPropertiesUpdating() throws Exception{
    configure("C.java");

    final int offset = myEditor.getCaretModel().getOffset();
    final PsiReference findReference = TargetElementUtil.findReference(myEditor, offset);
    final PsiPolyVariantReference reference = assertInstanceOf(findReference, PsiPolyVariantReference.class);
    assertSize(1, reference.multiResolve(false));

    final PropertiesFile propertiesFile = findPropertiesFile();
    ApplicationManager.getApplication().runWriteAction(() -> {
      propertiesFile.addProperty("new", "property");
    });

    assertSize(1, reference.multiResolve(false));
  }

  private void checkNavigation() {
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiElement elementAtCaret = myFile.findElementAt(offset);
    assertNotNull(elementAtCaret);
    final PsiElement targetElement = GotoDeclarationAction.findTargetElement(myProject, myEditor, offset);
    final PropertiesFile propertiesFile = findPropertiesFile();
    final IProperty property = assertInstanceOf(targetElement, IProperty.class);
    assertEquals(property.getPropertiesFile(), propertiesFile);

    final String elementText = elementAtCaret.getText();
    final String unescapedKey = property.getUnescapedKey();
    assertNotNull(unescapedKey);
    assertTrue(elementText.contains(unescapedKey));
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/";
  }

  private void configure(@NonNls final String fileName) throws Exception {
    configureByFile(BASE_PATH + fileName, BASE_PATH);
    ((StartupManagerImpl)StartupManager.getInstance(myProject)).runPostStartupActivities();
  }

  private PropertiesFile findPropertiesFile() {
    return (PropertiesFile)myFile.getContainingDirectory().findFile("myprops.properties");
  }

}
