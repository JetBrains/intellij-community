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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * Created by Max Medvedev on 05/02/14
 */
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
  public PsiElement getHighlightElementForCategoryQualifier() {
    GrExpression invoked = getCall().getInvokedExpression();
    if (invoked instanceof GrReferenceExpression) {
      PsiElement nameElement = ((GrReferenceExpression)invoked).getReferenceNameElement();
      if (nameElement != null) {
        return nameElement;
      }
    }
    return invoked;
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
