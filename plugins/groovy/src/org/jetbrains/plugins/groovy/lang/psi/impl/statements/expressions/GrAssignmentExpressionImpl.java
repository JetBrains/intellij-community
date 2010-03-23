/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTupleDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

/**
 * @author ilyas
 */
public class GrAssignmentExpressionImpl extends GrExpressionImpl implements GrAssignmentExpression {

  public GrAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Assignment expression";
  }

  public boolean isTupleAssignment() {
    return getFirstChild() instanceof GrTupleDeclaration;
  }

  @NotNull
  public GrExpression getLValue() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Nullable
  public GrExpression getRValue() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 1) {
      return exprs[1];
    }
    return null;
  }

  public IElementType getOperationToken() {
    return findChildByType(GroovyTokenTypes.ASSIGN_OP_SET).getNode().getElementType();
  }

  public PsiType getType() {
    return getLValue().getType();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    if (lastParent != null) {
      return true;
    }

    GrExpression lValue = getLValue();
    if (lValue instanceof GrReferenceExpression) {
      String refName = processor instanceof ResolverProcessor ? ((ResolverProcessor) processor).getName() : null;
      if (isDeclarationAssignment((GrReferenceExpression) lValue, refName)) {
        if (!processor.execute(lValue, ResolveState.initial())) return false;
      }
    }

    return true;
  }

  private static boolean isDeclarationAssignment(@NotNull GrReferenceExpression lRefExpr, @Nullable String nameHint) {
    if (nameHint == null || nameHint.equals(lRefExpr.getName())) {
      final PsiElement target = lRefExpr.resolve(); //this is NOT quadratic since the next statement will prevent from further processing declarations upstream
      if (!(target instanceof PsiVariable) && !(target instanceof GrAccessorMethod)) {
        return true;
      }
    }
    return false;
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAssignmentExpression(this);
  }
}
