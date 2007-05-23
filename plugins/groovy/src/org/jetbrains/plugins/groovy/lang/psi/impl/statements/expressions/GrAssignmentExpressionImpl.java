/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

/**
 * @author ilyas
 */
public class GrAssignmentExpressionImpl extends GroovyPsiElementImpl implements GrAssignmentExpression {

  public GrAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Assignment expression";
  }

  public GrExpression getLValue() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 0) {
      return exprs[0];
    }
    return null;
  }

  public GrExpression getRValue() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 1) {
      return exprs[1];
    }
    return null;
  }

  public PsiType getType() {
    return getLValue().getType();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    GrExpression lValue = getLValue();
    if (lastParent == null || !PsiTreeUtil.isAncestor(this, lastParent, false)) {
      if (lValue instanceof GrReferenceExpressionImpl) {
        GrReferenceExpressionImpl lRefExpr = (GrReferenceExpressionImpl) lValue;
        String name = lRefExpr.getName();
        String refName = processor instanceof ResolverProcessor ? ((ResolverProcessor) processor).getName() : null;
        if (refName == null ||
            (refName.equals(name) && !(lRefExpr.resolve() instanceof PsiVariable))) { //this is NOT quadratic since the next statement will prevent from further processing declarations upstream
          if (!processor.execute(lRefExpr, PsiSubstitutor.EMPTY)) return false;
        }
      }
    }

    return true;
  }
}
