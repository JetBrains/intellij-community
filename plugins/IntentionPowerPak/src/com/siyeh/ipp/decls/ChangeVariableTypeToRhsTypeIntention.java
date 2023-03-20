// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.decls;

import com.intellij.psi.*;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ChangeVariableTypeToRhsTypeIntention extends MutablyNamedIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("change.variable.type.to.rhs.type.intention.family.name");
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ChangeVariableTypeToRhsTypePredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiVariable variable = (PsiVariable)element.getParent();
    final PsiExpression initializer = variable.getInitializer();
    assert initializer != null;
    final PsiType type = initializer.getType();
    assert type != null;
    return IntentionPowerPackBundle.message("change.variable.type.to.rhs.type.intention.name",
                                            variable.getName(), type.getPresentableText());
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiVariable variable)) {
      return;
    }
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      return;
    }
    final PsiType type = initializer.getType();
    if (type == null) {
      return;
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    final PsiTypeElement typeElement = factory.createTypeElement(type);
    final PsiTypeElement variableTypeElement = variable.getTypeElement();
    if (variableTypeElement == null) {
      return;
    }
    new CommentTracker().replaceAndRestoreComments(variableTypeElement, typeElement);
  }
}
