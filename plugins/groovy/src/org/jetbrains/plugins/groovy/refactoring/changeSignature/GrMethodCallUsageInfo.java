/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;

/**
 * @author Maxim.Medvedev
 */
public class GrMethodCallUsageInfo extends UsageInfo {
  private final boolean myToChangeArguments;
  private final boolean myToCatchExceptions;
  private final PsiMethod myReferencedMethod;

  public boolean isToCatchExceptions() {
    return myToCatchExceptions;
  }

  public boolean isToChangeArguments() {
    return myToChangeArguments;
  }

  public GrMethodCallUsageInfo(PsiElement element, boolean isToChangeArguments, boolean isToCatchExceptions) {
    super(element);
    myToChangeArguments = isToChangeArguments;
    myToCatchExceptions = isToCatchExceptions;
    myReferencedMethod = resolveMethod(element);
  }

  @Nullable
  private static PsiMethod resolveMethod(final PsiElement ref) {
    if (ref instanceof GrEnumConstant) return ((GrEnumConstant)ref).resolveConstructor();
    PsiElement parent = ref.getParent();
    if (parent instanceof GrCallExpression) {
      return ((GrCallExpression)parent).resolveMethod();
    }
    else if (parent instanceof GrApplicationStatement) {
      final GrExpression expression = ((GrApplicationStatement)parent).getFunExpression();
      if (expression instanceof GrReferenceExpression) {
        final PsiElement element = ((GrReferenceExpression)expression).resolve();
        if (element instanceof PsiMethod) {
          return (PsiMethod)element;
        }
      }
    }
    else if (parent instanceof GrConstructorInvocation) {
      return ((PsiNewExpression)parent.getParent()).resolveConstructor();
    }

    return null;
  }

  public PsiMethod getReferencedMethod() {
    return myReferencedMethod;
  }


}
