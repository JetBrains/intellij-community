// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class PropertiesFileRenameReferenceTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/fileRename";
  }

  public void testRenamePropertiesFile() {
    final PsiFile[] files = myFixture.configureByFiles("i18n.properties", "MyClass.java");
    final PsiFile propertiesFile = files[0];
    final PsiFile javaSourceFile = files[1];
    myFixture.renameElement(propertiesFile, "i19n.properties");
    boolean[] found = {false};
    PsiTreeUtil.processElements(javaSourceFile, new PsiElementProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element) {
        if (PlatformPatterns.psiElement(PsiField.class).withName("BUNDLE_NAME").accepts(element)) {
          assertEquals("i19n", ((PsiLiteralExpression)((PsiField)element).getInitializer()).getValue());
          found[0] = true;
        }
        return true;
      }
    });
    assertTrue(found[0]);
  }
}
