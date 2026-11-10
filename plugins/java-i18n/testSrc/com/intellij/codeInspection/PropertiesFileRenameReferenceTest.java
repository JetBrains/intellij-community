// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class PropertiesFileRenameReferenceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/fileRename");

  @Test
  public void testRenamePropertiesFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile[] files = myFixture.configureByFiles("i18n.properties", "MyClass.java");
    final PsiFile propertiesFile = files[0];
    final PsiFile javaSourceFile = files[1];
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }
}
