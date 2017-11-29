// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GrConstructorInvocationInfo extends ConstructorCallInfoBase<GrConstructorInvocation>
  implements ConstructorCallInfo<GrConstructorInvocation> {
  public GrConstructorInvocationInfo(GrConstructorInvocation call) {
    super(call);
  }

  @Nullable
  @Override
  protected PsiType[] inferArgTypes() {
    return PsiUtil.getArgumentTypes(getArgumentList());
  }

  @NotNull
  @Override
  public GrExpression getInvokedExpression() {
    return getCall().getInvokedExpression();
  }

  @Nullable
  @Override
  public PsiType getQualifierInstanceType() {
    return getInvokedExpression().getType();
  }

  @NotNull
  @Override
  public PsiElement getElementToHighlight() {
    return getCall().getArgumentList();
  }
}
