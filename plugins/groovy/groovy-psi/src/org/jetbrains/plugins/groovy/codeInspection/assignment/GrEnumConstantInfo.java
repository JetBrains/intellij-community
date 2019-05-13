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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GrEnumConstantInfo extends ConstructorCallInfoBase<GrEnumConstant> implements ConstructorCallInfo<GrEnumConstant> {
  public GrEnumConstantInfo(GrEnumConstant constant) {
    super(constant);
  }

  @Nullable
  @Override
  protected PsiType[] inferArgTypes() {
    GrEnumConstant call = getCall();
    GrArgumentList argList = call.getArgumentList();
    if (argList != null) {
      return PsiUtil.getArgumentTypes(argList);
    }
    else {
      return PsiType.EMPTY_ARRAY;
    }
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
    GrEnumConstant constant = getCall();
    GrArgumentList argList = constant.getArgumentList();
    if (argList != null) return argList;

    return constant.getNameIdentifierGroovy();
  }
}
