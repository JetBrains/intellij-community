package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import java.util.Objects;

public class JourneyNodeIdentity {
  private final SmartPsiElementPointer identifier;

  public SmartPsiElementPointer getIdentifierElement() {
    return identifier;
  }

  public JourneyNodeIdentity(@NotNull SmartPsiElementPointer psiElement) {
    identifier = psiElement;
  }

  public PsiFile getFile() {
    return ReadAction.compute(() -> identifier.getContainingFile());
  }

  public @NotNull PsiMember getMember() {
    return Objects.requireNonNull((PsiMember)PsiUtil.tryFindParentOrNull(identifier.getElement(), it -> it instanceof PsiMember));
    // TODO handle member is null
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    JourneyNodeIdentity identity = (JourneyNodeIdentity)o;
    return Objects.equals(getFile(), identity.getFile());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getFile());
  }
}
