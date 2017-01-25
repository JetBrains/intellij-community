/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrExpressionTypeCalculator;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo;
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToUtilKt;

public class GrClosureDelegateTypeCalculator extends GrExpressionTypeCalculator {

  @Nullable
  @Override
  public PsiType calculateType(@NotNull GrExpression expression, @Nullable PsiElement resolved) {
    if (expression instanceof GrReferenceExpression && resolved instanceof PsiMethod) {
      return calculateType(expression, (PsiMethod)resolved);
    }
    return null;
  }

  @Nullable
  protected PsiType calculateType(@NotNull GrExpression expression, @NotNull PsiMethod method) {
    if (!"getDelegate".equals(method.getName()) || method.getParameterList().getParametersCount() != 0) return null;

    final GrClosableBlock closure = PsiTreeUtil.getParentOfType(expression, GrClosableBlock.class);
    if (closure == null) return null;

    final PsiClass closureClass = JavaPsiFacade.getInstance(expression.getProject()).findClass(
      GroovyCommonClassNames.GROOVY_LANG_CLOSURE, expression.getResolveScope()
    );
    if (closureClass == null || !closureClass.equals(method.getContainingClass())) return null;

    final DelegatesToInfo info = GrDelegatesToUtilKt.getDelegatesToInfo(closure);
    if (info == null) return null;

    return info.getTypeToDelegate();
  }
}
