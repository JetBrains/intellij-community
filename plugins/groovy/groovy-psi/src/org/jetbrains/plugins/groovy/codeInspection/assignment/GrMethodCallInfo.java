// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GrMethodCallInfo extends CallInfoBase<GrMethodCall> implements CallInfo<GrMethodCall> {
  public GrMethodCallInfo(GrMethodCall call) {
    super(call);
  }

  @Nullable
  @Override
  protected PsiType[] inferArgTypes() {
    return PsiUtil.getArgumentTypes(getCall().getInvokedExpression(), true);
  }

  @Override
  public GrExpression getInvokedExpression() {
    return getCall().getInvokedExpression();
  }

  @Override
  public PsiType getQualifierInstanceType() {
    GrExpression invoked = getCall().getInvokedExpression();
    return invoked instanceof GrReferenceExpression ? PsiImplUtil.getQualifierType((GrReferenceExpression)invoked) : null;
  }

  @NotNull
  @Override
  public PsiElement getElementToHighlight() {
    GrArgumentList argList = getCall().getArgumentList();
    if (argList.getTextLength() == 0) {
      return getCall();
    }
    return argList;
  }
}
