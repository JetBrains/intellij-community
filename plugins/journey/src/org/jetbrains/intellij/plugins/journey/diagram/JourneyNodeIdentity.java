package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.LazyPsiElementHolder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public record JourneyNodeIdentity(
  @NotNull
  PsiElement element
) implements LazyPsiElementHolder {
  @Override
  public @NotNull PsiElement calculatePsiElement() {
    return element;
  }
}
