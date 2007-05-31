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
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrConditionalExprImpl extends GrExpressionImpl implements GrConditionalExpression {

  public GrConditionalExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Conditional expression";
  }

  public GrExpression getCondition() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 0) {
      return exprs[0];
    }
    return null;
  }

  public GrExpression getThenBranch() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 1) {
      return exprs[1];
    }
    return null;
  }

  public GrExpression getElseBranch() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 2) {
      return exprs[2];
    }
    return null;
  }

  public PsiType getType() {
    return null;
  }
}