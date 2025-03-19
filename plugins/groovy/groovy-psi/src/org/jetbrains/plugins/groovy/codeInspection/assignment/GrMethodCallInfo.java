// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GrMethodCallInfo extends CallInfoBase<GrMethodCall> implements CallInfo<GrMethodCall> {
  public GrMethodCallInfo(GrMethodCall call) {
    super(call);
  }

  @Override
  protected PsiType @Nullable [] inferArgTypes() {
    return PsiUtil.getArgumentTypes(getCall().getInvokedExpression(), true);
  }

  public @NotNull GroovyResolveResult advancedResolve() {
    return getCall().advancedResolve();
  }

  @Override
  public GrExpression getInvokedExpression() {
    return getCall().getInvokedExpression();
  }

  @Override
  public @NotNull PsiElement getElementToHighlight() {
    GrArgumentList argList = getCall().getArgumentList();
    if (argList.getTextLength() == 0) {
      return getCall();
    }
    return argList;
  }

  public GroovyResolveResult @NotNull [] multiResolve() {
    return getCall().multiResolve(false);
  }
}
