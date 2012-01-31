package org.jetbrains.android.inspections.lint;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
interface AndroidLintQuickFix {
  AndroidLintQuickFix[] EMPTY_ARRAY = new AndroidLintQuickFix[0];
  
  void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @Nullable Editor editor);

  boolean isApplicable(@NotNull PsiElement startElement, @NotNull PsiElement endElement, boolean inBatchMode);

  @NotNull
  String getName();
}
