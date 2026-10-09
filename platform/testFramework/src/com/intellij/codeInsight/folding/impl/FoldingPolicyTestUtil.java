// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Folding policy checks for a JUnit 5 test. This is the replacement of {@link AbstractFoldingPolicyTest}.
 */
@TestOnly
public final class FoldingPolicyTestUtil {
  private FoldingPolicyTestUtil() {
  }

  /**
   * Configures the file {@code test.<ext>} with {@code text} and builds the initial fold regions.
   * Then checks that {@link FoldingPolicy} restores the PSI element of each fold region by its signature.
   * Call it from any thread.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture, @NotNull String text, @NotNull String ext) {
    fixture.configureByText("test." + ext, text);

    Editor editor = fixture.getEditor();
    FoldRegion[] foldRegions = EdtTestUtil.runInEdtAndGet(() -> {
      EditorTestUtil.buildInitialFoldingsInBackground(editor);
      return editor.getFoldingModel().getAllFoldRegions();
    });
    assertTrue(foldRegions.length > 0);
    EditorFoldingInfo info = EditorFoldingInfo.get(editor);
    ReadAction.runBlocking(() -> {
      for (FoldRegion region : foldRegions) {
        PsiElement element = info.getPsiElement(region);
        if (element == null) {
          continue;
        }
        String signature = FoldingPolicy.getSignature(element);
        assertNotNull(signature);
        assertEquals(element, FoldingPolicy.restoreBySignature(element.getContainingFile(), signature));
      }
    });
  }
}
