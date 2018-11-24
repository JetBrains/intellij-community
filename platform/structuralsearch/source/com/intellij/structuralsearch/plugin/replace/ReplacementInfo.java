package com.intellij.structuralsearch.plugin.replace;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public abstract class ReplacementInfo {
  public abstract String getReplacement();

  public abstract void setReplacement(String replacement);

  @Nullable
  public abstract PsiElement getMatch(int index);

  public abstract int getMatchesCount();
}
