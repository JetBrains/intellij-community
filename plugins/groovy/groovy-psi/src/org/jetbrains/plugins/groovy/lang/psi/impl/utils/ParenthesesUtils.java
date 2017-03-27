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
package org.jetbrains.plugins.groovy.lang.psi.impl.utils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;

import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.ASSOCIATIVE_BINARY_OP_SET;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.PARENTHESIZED_BINARY_OP_SET;

/**
 * Precedence documentation - http://groovy-lang.org/operators.html#_operator_precedence
 */
public class ParenthesesUtils {

  private ParenthesesUtils() {
  }

  private static final int PARENTHESIZED_PRECEDENCE;
  private static final int NEW_EXPR_PRECEDENCE;
  private static final int LITERAL_PRECEDENCE;
  public static final int METHOD_CALL_PRECEDENCE;

  private static final int POSTFIX_PRECEDENCE;
  public static final int PREFIX_PRECEDENCE;
  public static final int TYPE_CAST_PRECEDENCE;
  public static final int EXPONENTIAL_PRECEDENCE;
  public static final int MULTIPLICATIVE_PRECEDENCE;
  private static final int ADDITIVE_PRECEDENCE;
  private static final int SHIFT_PRECEDENCE;
  private static final int RANGE_PRECEDENCE;
  public static final int INSTANCEOF_PRECEDENCE;
  public static final int RELATIONAL_PRECEDENCE;
  public static final int EQUALITY_PRECEDENCE;

  private static final int BINARY_AND_PRECEDENCE;
  private static final int BINARY_XOR_PRECEDENCE;
  private static final int BINARY_OR_PRECEDENCE;
  public static final int AND_PRECEDENCE;
  public static final int OR_PRECEDENCE;
  public static final int CONDITIONAL_PRECEDENCE;
  public static final int SAFE_CAST_PRECEDENCE;
  private static final int ASSIGNMENT_PRECEDENCE;
  private static final int APPLICATION_STMT_PRECEDENCE;

  static {
    int i = 0;

    PARENTHESIZED_PRECEDENCE = 0;
    LITERAL_PRECEDENCE = 0;
    NEW_EXPR_PRECEDENCE = 0;
    METHOD_CALL_PRECEDENCE = 0;
    TYPE_CAST_PRECEDENCE = i++;
    POSTFIX_PRECEDENCE = i++;
    EXPONENTIAL_PRECEDENCE = i++;
    PREFIX_PRECEDENCE = i++;
    MULTIPLICATIVE_PRECEDENCE = i++;
    ADDITIVE_PRECEDENCE = i++;
    SHIFT_PRECEDENCE = i++;
    RANGE_PRECEDENCE = i++;
    INSTANCEOF_PRECEDENCE = i++;
    RELATIONAL_PRECEDENCE = i++;
    EQUALITY_PRECEDENCE = i++;

    BINARY_AND_PRECEDENCE = i++;
    BINARY_XOR_PRECEDENCE = i++;
    BINARY_OR_PRECEDENCE = i++;
    AND_PRECEDENCE = i++;
    OR_PRECEDENCE = i++;
    CONDITIONAL_PRECEDENCE = i++;
    SAFE_CAST_PRECEDENCE = i++;
    ASSIGNMENT_PRECEDENCE = i++;
    APPLICATION_STMT_PRECEDENCE = i;
  }

  private static final Map<IElementType, Integer> BINARY_PRECEDENCES = new HashMap<>();

  static {
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mPLUS, ADDITIVE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mMINUS, ADDITIVE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mSTAR, MULTIPLICATIVE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mDIV, MULTIPLICATIVE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mMOD, MULTIPLICATIVE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mSTAR_STAR, EXPONENTIAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mLAND, AND_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mLOR, OR_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mBAND, BINARY_AND_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mBOR, BINARY_OR_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mBXOR, BINARY_XOR_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyElementTypes.COMPOSITE_LSHIFT_SIGN, SHIFT_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyElementTypes.COMPOSITE_RSHIFT_SIGN, SHIFT_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN, SHIFT_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mRANGE_INCLUSIVE, RANGE_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mRANGE_EXCLUSIVE, RANGE_PRECEDENCE);

    BINARY_PRECEDENCES.put(GroovyTokenTypes.mGT, RELATIONAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mGE, RELATIONAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mLT, RELATIONAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mLE, RELATIONAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mEQUAL, EQUALITY_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.kIN, RELATIONAL_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mNOT_EQUAL, EQUALITY_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.mCOMPARE_TO, EQUALITY_PRECEDENCE);
    BINARY_PRECEDENCES.put(GroovyTokenTypes.kAS, SAFE_CAST_PRECEDENCE);
  }

  public static int precedenceForBinaryOperator(@NotNull IElementType sign) {
    return BINARY_PRECEDENCES.get(sign);
  }



  public static int getPrecedence(GrExpression expr) {
    if (expr instanceof GrUnaryExpression) return ((GrUnaryExpression)expr).isPostfix() ? POSTFIX_PRECEDENCE : PREFIX_PRECEDENCE;
    if (expr instanceof GrTypeCastExpression) return TYPE_CAST_PRECEDENCE;
    if (expr instanceof GrConditionalExpression) return CONDITIONAL_PRECEDENCE;
    if (expr instanceof GrSafeCastExpression) return SAFE_CAST_PRECEDENCE;
    if (expr instanceof GrAssignmentExpression) return ASSIGNMENT_PRECEDENCE;
    if (expr instanceof GrApplicationStatement) return APPLICATION_STMT_PRECEDENCE;
    if (expr instanceof GrInstanceOfExpression) return INSTANCEOF_PRECEDENCE;
    if (expr instanceof GrNewExpression) return NEW_EXPR_PRECEDENCE;
    if (expr instanceof GrParenthesizedExpression) return PARENTHESIZED_PRECEDENCE;
    if (expr instanceof GrReferenceExpression) {
      final GrReferenceExpression referenceExpression = (GrReferenceExpression)expr;
      return referenceExpression.getQualifierExpression() == null ? LITERAL_PRECEDENCE : METHOD_CALL_PRECEDENCE;
    }
    if (expr instanceof GrBinaryExpression) {
      final IElementType opToken = ((GrBinaryExpression)expr).getOperationTokenType();
      return precedenceForBinaryOperator(opToken);
    }
    return  0;
  }

  @NotNull
  public static GrExpression parenthesize(@NotNull GrExpression expression) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expression.getProject());
    return factory.createParenthesizedExpr(expression);
  }

  @NotNull
  public static GrExpression unparenthesize(@NotNull GrExpression expression) {
    GrExpression currentExpression = expression;
    while (currentExpression instanceof GrParenthesizedExpression) {
      GrExpression operand = ((GrParenthesizedExpression)currentExpression).getOperand();
      if (operand != null) {
        currentExpression = operand;
      } else {
        return currentExpression;
      }
    }
    return currentExpression;
  }

  public static boolean checkPrecedenceForBinaryOps(int precedence, @NotNull IElementType parentToken, boolean isRhs) {
    int parentPrecedence = precedenceForBinaryOperator(parentToken);
    if (precedence > parentPrecedence) return true;
    if (precedence == parentPrecedence && parentPrecedence != 0) {
      if (!ASSOCIATIVE_BINARY_OP_SET.contains(parentToken) && isRhs ||
          PARENTHESIZED_BINARY_OP_SET.contains(parentToken)) {
        return true;
      }
    }
    return false;
  }

  public static boolean checkPrecedenceForNonBinaryOps(@NotNull GrExpression newExpr, int parentPrecedence) {
    return checkPrecedenceForNonBinaryOps(getPrecedence(newExpr), parentPrecedence);
  }

  public static boolean checkPrecedenceForNonBinaryOps(int precedence, int parentPrecedence) {
    return precedence > parentPrecedence;
  }

  public static boolean checkPrecedence(int precedence, @NotNull GrExpression oldExpr) {
    PsiElement parent = oldExpr.getParent();
    if (parent instanceof GrArgumentList) {
      parent = parent.getParent();
    }
    if (!(parent instanceof GrExpression)) return false;
    GrExpression oldParent = (GrExpression) parent;
    if (oldParent instanceof GrBinaryExpression) {
      GrBinaryExpression binaryExpression = (GrBinaryExpression)oldParent;
      GrExpression rightOperand = binaryExpression.getRightOperand();
      return checkPrecedenceForBinaryOps(precedence, binaryExpression.getOperationTokenType(), oldExpr.equals(rightOperand));
    } else {
      return checkPrecedenceForNonBinaryOps(precedence, getPrecedence(oldParent));
    }
  }

  public static boolean checkPrecedence(@NotNull GrExpression newExpr, @NotNull GrExpression oldExpr) {
    return checkPrecedence(getPrecedence(newExpr), oldExpr);
  }
}
