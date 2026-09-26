// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.declaration;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

/**
 * @author Max Medvedev
 */
public abstract class GrChangeMemberVisibilityModifierBase extends GrPsiUpdateIntention {
  private final String myModifier;

  public GrChangeMemberVisibilityModifierBase(String modifier) {
    myModifier = modifier;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof GrMember)) return;

    ((GrMember)parent).getModifierList().setModifierProperty(myModifier, true);
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        final PsiElement parent = element.getParent();
        return parent instanceof GrMember &&
               parent instanceof GrNamedElement &&
               (((GrNamedElement)parent).getNameIdentifierGroovy() == element || ((GrMember)parent).getModifierList() == element) &&
               ((GrMember)parent).getModifierList() != null && !((GrMember)parent).getModifierList().hasExplicitModifier(myModifier);
      }
    };
  }
}
