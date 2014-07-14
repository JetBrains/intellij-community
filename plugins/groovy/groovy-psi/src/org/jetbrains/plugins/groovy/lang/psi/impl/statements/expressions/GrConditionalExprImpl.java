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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;

/**
 * @author ilyas
 */
public class GrConditionalExprImpl extends GrExpressionImpl implements GrConditionalExpression {
  private static final Function<GrConditionalExpression, PsiType> TYPE_CALCULATOR = new NullableFunction<GrConditionalExpression, PsiType>() {
    @Override
    @Nullable
    public PsiType fun(GrConditionalExpression conditional) {
      GrExpression thenBranch = conditional.getThenBranch();
      GrExpression elseBranch = conditional.getElseBranch();
      if (thenBranch == null) {
        if (elseBranch != null) return elseBranch.getType();
      }
      else {
        if (elseBranch == null) return thenBranch.getType();
        PsiType thenType = thenBranch.getType();
        PsiType elseType = elseBranch.getType();
        return TypesUtil.getLeastUpperBoundNullable(thenType, elseType, conditional.getManager());
      }
      return null;
    }
  };

  public GrConditionalExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Conditional expression";
  }

  @Override
  @NotNull
  public GrExpression getCondition() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  @Nullable
  public GrExpression getThenBranch() {
    final PsiElement question = findChildByType(GroovyTokenTypes.mQUESTION);
    for (PsiElement nextSibling = question;
         nextSibling != null && nextSibling.getNode().getElementType() != GroovyTokenTypes.mCOLON;
         nextSibling = nextSibling.getNextSibling()) {
      if (nextSibling instanceof GrExpression) return (GrExpression)nextSibling;
    }
    return null;
  }

  @Override
  @Nullable
  public GrExpression getElseBranch() {
    final PsiElement colon = findChildByType(GroovyTokenTypes.mCOLON);
    for (PsiElement nextSibling = colon;
         nextSibling != null;
         nextSibling = nextSibling.getNextSibling()) {
      if (nextSibling instanceof GrExpression) return (GrExpression)nextSibling;
    }
    return null;
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPE_CALCULATOR);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitConditionalExpression(this);
  }
}
