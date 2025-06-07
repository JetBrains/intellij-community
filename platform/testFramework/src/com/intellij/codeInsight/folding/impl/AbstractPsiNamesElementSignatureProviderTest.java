// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractPsiNamesElementSignatureProviderTest extends BasePlatformTestCase {
  public void doTest(@NotNull String text, @NotNull String ext) {
    myFixture.configureByText("test." + ext, text);

    EditorTestUtil.buildInitialFoldingsInBackground(myFixture.getEditor());
    FoldRegion[] foldRegions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
    assertTrue(foldRegions.length > 1);
    for (FoldRegion region : foldRegions) {
      PsiElement element = findElement(region, myFixture.getFile());
      if (element == null) {
        continue;
      }
      PsiNamesElementSignatureProvider provider = new PsiNamesElementSignatureProvider();
      String signature = provider.getSignature(element);
      assertNotNull(signature);
      assertEquals(element, provider.restoreBySignature(myFixture.getFile(), signature, null));
    }
  }

  private static @Nullable PsiElement findElement(@NotNull FoldRegion region, @NotNull PsiFile file) {
    return findElement(region.getStartOffset(), region.getEndOffset(), file);
  }

  public static @Nullable PsiElement findElement(int startOffset, int endOffset, @NotNull PsiFile file) {
    for (PsiElement element = file.findElementAt(startOffset); element != null; element = element.getParent()) {
      TextRange range = element.getTextRange();
      if (range.getStartOffset() < startOffset) {
        return null;
      }
      if (range.getStartOffset() == startOffset && range.getEndOffset() == endOffset) {
        return element;
      }
    }
    return null;
  }
}
