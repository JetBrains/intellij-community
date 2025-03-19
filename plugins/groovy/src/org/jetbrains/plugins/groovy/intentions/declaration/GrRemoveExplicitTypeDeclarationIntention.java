// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.declaration;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

public final class GrRemoveExplicitTypeDeclarationIntention extends GrPsiUpdateIntention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    PsiElement parent = element.getParent();

    if (parent instanceof GrVariable) {
      ((GrVariable)parent).setType(null);
    }
    else if (parent instanceof GrVariableDeclaration) {
      ((GrVariableDeclaration)parent).setType(null);
    }
    else if (parent instanceof GrMethod) {
      ((GrMethod)parent).setReturnType(null);
    }
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        PsiElement parent = element.getParent();
        if (element instanceof GrTypeElement || element instanceof GrModifierList) {
          return parent instanceof GrVariableDeclaration && ((GrVariableDeclaration)parent).getTypeElementGroovy() != null ||
                 parent instanceof GrMethod && ((GrMethod)parent).getReturnTypeElementGroovy() != null;
        }

        if (parent instanceof GrNamedElement && ((GrNamedElement)parent).getNameIdentifierGroovy().equals(element)) {
          if (parent instanceof GrVariable) {
            return ((GrVariable)parent).getTypeElementGroovy() != null;
          }

          if (parent instanceof GrMethod && ((GrMethod)parent).findSuperMethods().length == 0 ) {
            return ((GrMethod)parent).getReturnTypeElementGroovy() != null;
          }
        }

        return false;
      }
    };
  }
}
