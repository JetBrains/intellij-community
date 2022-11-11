// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public class CreateSetterFromUsageFix extends CreateMethodFromUsageFix implements LowPriorityAction {
  public CreateSetterFromUsageFix(@NotNull GrReferenceExpression refExpression) {
    super(refExpression);
  }

  @Override
  protected TypeConstraint @NotNull [] getReturnTypeConstraints() {
    return new TypeConstraint[]{SubtypeConstraint.create(PsiType.VOID)};
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    GrReferenceExpression expr = getRefExpr();
    if (expr == null) {
      return null;
    }
    return new CreateSetterFromUsageFix(expr);
  }

  @Override
  protected PsiType[] getArgumentTypes() {
    final GrReferenceExpression ref = getRefExpr();
    assert PsiUtil.isLValue(ref);
    PsiType initializer = TypeInferenceHelper.getInitializerTypeFor(ref);
    if (initializer == null || initializer == PsiType.NULL) {
      initializer = TypesUtil.getJavaLangObject(ref);
    }
    return new PsiType[]{initializer};
  }

  @NotNull
  @Override
  protected String getMethodName() {
    return GroovyPropertyUtils.getSetterName(getRefExpr().getReferenceName());
  }
}
