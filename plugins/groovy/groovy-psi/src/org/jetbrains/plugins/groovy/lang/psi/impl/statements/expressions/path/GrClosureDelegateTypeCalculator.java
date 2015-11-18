/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrDelegatesToUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrExpressionTypeCalculator;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

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

    final PsiClass closureClass = GroovyPsiManager.getInstance(expression.getProject()).findClassWithCache(
      GroovyCommonClassNames.GROOVY_LANG_CLOSURE, expression.getResolveScope()
    );
    if (closureClass == null || !closureClass.equals(method.getContainingClass())) return null;

    final GrDelegatesToUtil.DelegatesToInfo info = GrDelegatesToUtil.getDelegatesToInfo(expression, closure);
    if (info == null) return null;

    return info.getTypeToDelegate();
  }
}
