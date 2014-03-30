/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * Created by Max Medvedev on 05/02/14
 */
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
  public PsiElement getHighlightElementForCategoryQualifier() {
    throw new UnsupportedOperationException("not applicable");
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
