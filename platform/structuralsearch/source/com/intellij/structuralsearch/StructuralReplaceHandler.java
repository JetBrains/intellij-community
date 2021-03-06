package com.intellij.structuralsearch;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class StructuralReplaceHandler {
  public abstract void replace(@NotNull ReplacementInfo info, @NotNull ReplaceOptions options);

  public void prepare(@NotNull ReplacementInfo info) {}

  public void postProcess(@NotNull PsiElement affectedElement, @NotNull ReplaceOptions options) {}
}
