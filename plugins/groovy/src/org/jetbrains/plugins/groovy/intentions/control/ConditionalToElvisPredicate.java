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

package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author ilyas
 */
public class ConditionalToElvisPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrConditionalExpression) ||
        ErrorUtil.containsError(element)) {
      return false;
    }
    GrConditionalExpression expr = (GrConditionalExpression) element;
    if (expr.getThenBranch() == null || expr.getElseBranch() == null) return false;
    GrExpression condition = expr.getCondition();
    if (condition instanceof GrBinaryExpression) {
      GrBinaryExpression binaryExpression = (GrBinaryExpression) condition;
      if (GroovyTokenTypes.mNOT_EQUAL == binaryExpression.getOperationTokenType()) {
        GrExpression left = binaryExpression.getLeftOperand();
        GrExpression right = binaryExpression.getRightOperand();
        if (left instanceof GrLiteral && "null".equals(left.getText()) && right != null) {
          return PsiEquivalenceUtil.areElementsEquivalent(right, expr.getThenBranch());
        }
        if (right instanceof GrLiteral && "null".equals(right.getText()) && left != null) {
          return PsiEquivalenceUtil.areElementsEquivalent(left, expr.getThenBranch());
        }
      }
    }

    return false;
    


  }

}
