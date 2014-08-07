package com.intellij.structuralsearch;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class StructuralReplaceHandler {
  public abstract void replace(final ReplacementInfo info, ReplaceOptions options);

  public void prepare(ReplacementInfo info) {}

  public void postProcess(PsiElement affectedElement, ReplaceOptions options) {}
}
