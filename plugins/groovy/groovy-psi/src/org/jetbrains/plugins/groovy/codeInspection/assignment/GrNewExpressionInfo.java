// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GrNewExpressionInfo extends ConstructorCallInfoBase<GrNewExpression> {

  public GrNewExpressionInfo(GrNewExpression expr) {
    super(expr);
  }

  @Nullable
  @Override
  protected PsiType[] inferArgTypes() {
    return PsiUtil.getArgumentTypes(getCall().getReferenceElement(), true);
  }

  @Nullable
  @Override
  public GrExpression getInvokedExpression() {
    return null;
  }

  @Nullable
  @Override
  public PsiType getQualifierInstanceType() {
    return null;
  }

  @NotNull
  @Override
  public PsiElement getElementToHighlight() {
    GrNewExpression call = getCall();

    GrArgumentList argList = call.getArgumentList();
    if (argList != null) return argList;

    GrCodeReferenceElement ref = call.getReferenceElement();
    if (ref != null) return ref;

    throw new IncorrectOperationException("reference of new expression should exist if it is a constructor call");
  }
}
