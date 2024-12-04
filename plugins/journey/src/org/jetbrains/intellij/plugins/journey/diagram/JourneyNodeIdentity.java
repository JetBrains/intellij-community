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

  public JourneyNodeIdentity(@NotNull PsiElement psiElement) {
    original = psiElement;
    file = ReadAction.compute(() -> psiElement.getContainingFile());
  }

  public PsiFile getFile() {
    return file;
  }

  @Override
  public @NotNull PsiFile calculatePsiElement() {
    return getFile();
  }

  public @NotNull PsiElement getOriginalElement() {
    return original;
  }

  public @NotNull PsiMember getMember() {
    return (PsiMember)Objects.requireNonNull(PsiUtil.tryFindParentOrNull(original, it -> it instanceof PsiMember));
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    JourneyNodeIdentity identity = (JourneyNodeIdentity)o;
    return Objects.equals(file, identity.file);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(file);
  }
}
