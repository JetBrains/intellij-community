/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
    return PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/rename";
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
