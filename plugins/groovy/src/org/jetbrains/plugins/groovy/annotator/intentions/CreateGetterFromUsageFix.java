// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

/**
 * @author Max Medvedev
 */
public class CreateGetterFromUsageFix extends CreateMethodFromUsageFix implements LowPriorityAction {
  public CreateGetterFromUsageFix(@NotNull GrReferenceExpression refExpression) {
    super(refExpression);
  }

  @Override
  protected TypeConstraint @NotNull [] getReturnTypeConstraints() {
    return GroovyExpectedTypesProvider.calculateTypeConstraints(getRefExpr());
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    GrReferenceExpression expr = getRefExpr();
    if (expr == null) {
      return null;
    }
    return new CreateGetterFromUsageFix(expr);
  }

  @Override
  protected PsiType[] getArgumentTypes() {
    return PsiType.EMPTY_ARRAY;
  }

  @Override
  protected @NotNull String getMethodName() {
    return GroovyPropertyUtils.getGetterNameNonBoolean(getRefExpr().getReferenceName());
  }
}
