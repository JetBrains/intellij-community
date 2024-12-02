package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.LazyPsiElementHolder;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import java.util.Objects;

public class JourneyNodeIdentity implements LazyPsiElementHolder {
  private final PsiFile file;
  private final PsiElement original;

  JourneyNodeIdentity(@NotNull PsiElement psiElement) {
    original = psiElement;
    file = ReadAction.nonBlocking(() -> psiElement.getContainingFile()).executeSynchronously();
  }

  @Override
  public @NotNull PsiFile calculatePsiElement() {
    return file;
  }

  public @NotNull PsiElement getOriginalElement() {
    return original;
  }

  public @NotNull PsiMember getOriginalMember() {
    return (PsiMember)Objects.requireNonNull(PsiUtil.tryFindParentOrNull(original, it -> it instanceof PsiMember));
  }
}
