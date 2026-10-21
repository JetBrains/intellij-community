// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PsiNamesElementSignatureProvider} checks for a JUnit 5 test.
 * This is the replacement of {@link AbstractPsiNamesElementSignatureProviderTest}.
 */
@TestOnly
public final class PsiNamesElementSignatureProviderTestUtil {
  private PsiNamesElementSignatureProviderTestUtil() {
  }

  /**
   * Configures the file {@code test.<ext>} with {@code text} and builds the initial fold regions.
   * Then checks that {@link PsiNamesElementSignatureProvider} restores the PSI element of each fold region by its signature.
   * Call it from any thread.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture, @NotNull String text, @NotNull String ext) {
    fixture.configureByText("test." + ext, text);

    Editor editor = fixture.getEditor();
    FoldRegion[] foldRegions = EdtTestUtil.runInEdtAndGet(() -> {
      EditorTestUtil.buildInitialFoldingsInBackground(editor);
      return editor.getFoldingModel().getAllFoldRegions();
    });
    assertTrue(foldRegions.length > 1);
    ReadAction.runBlocking(() -> {
      PsiFile file = fixture.getFile();
      for (FoldRegion region : foldRegions) {
        PsiElement element = findElement(region.getStartOffset(), region.getEndOffset(), file);
        if (element == null) {
          continue;
        }
        PsiNamesElementSignatureProvider provider = new PsiNamesElementSignatureProvider();
        String signature = provider.getSignature(element);
        assertNotNull(signature);
        assertEquals(element, provider.restoreBySignature(file, signature, null));
      }
    });
  }

  /**
   * Returns the PSI element at {@code startOffset} whose text range is exactly {@code [startOffset, endOffset)}, or {@code null}.
   * Call it in a read action.
   */
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
