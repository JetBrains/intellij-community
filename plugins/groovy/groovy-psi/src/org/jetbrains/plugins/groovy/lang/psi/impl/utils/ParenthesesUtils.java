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

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.HashMap;
import java.util.Map;

public class ParenthesesUtils {

  private ParenthesesUtils() {
  }

  private static final int PARENTHESIZED_PRECEDENCE;
  private static final int LITERAL_PRECEDENCE;
  public static final int METHOD_CALL_PRECEDENCE;

  private static final int POSTFIX_PRECEDENCE;
  public static final int PREFIX_PRECEDENCE;
  public static final int TYPE_CAST_PRECEDENCE;
  public static final int EXPONENTIAL_PRECEDENCE;
  public static final int MULTIPLICATIVE_PRECEDENCE;
  private static final int ADDITIVE_PRECEDENCE;
  private static final int SHIFT_PRECEDENCE;
  public static final int INSTANCEOF_PRECEDENCE;
  private static final int RELATIONAL_PRECEDENCE;
  public static final int EQUALITY_PRECEDENCE;

  private static final int BINARY_AND_PRECEDENCE;
  private static final int BINARY_XOR_PRECEDENCE;
  private static final int BINARY_OR_PRECEDENCE;
  public static final int AND_PRECEDENCE;
  public static final int OR_PRECEDENCE;
  public static final int CONDITIONAL_PRECEDENCE;
  private static final int ASSIGNMENT_PRECEDENCE;

  private static final int NUM_PRECEDENCES;

  static {
    int i = 0;
    PARENTHESIZED_PRECEDENCE = 0;
    LITERAL_PRECEDENCE = i++;
    METHOD_CALL_PRECEDENCE = i++;

    POSTFIX_PRECEDENCE = i++;
    PREFIX_PRECEDENCE = i++;
    TYPE_CAST_PRECEDENCE = i++;
    EXPONENTIAL_PRECEDENCE = i++;
    MULTIPLICATIVE_PRECEDENCE = i++;
    ADDITIVE_PRECEDENCE = i++;
    SHIFT_PRECEDENCE = i++;
    INSTANCEOF_PRECEDENCE = i++;
    RELATIONAL_PRECEDENCE = i++;
    EQUALITY_PRECEDENCE = i++;

    BINARY_AND_PRECEDENCE = i++;
    BINARY_XOR_PRECEDENCE = i++;
    BINARY_OR_PRECEDENCE = i++;
    AND_PRECEDENCE = i++;
    OR_PRECEDENCE = i++;
    CONDITIONAL_PRECEDENCE = i++;
    ASSIGNMENT_PRECEDENCE = i++;

    NUM_PRECEDENCES = i;
  }

  private static final Map<IElementType, Integer> s_binaryOperatorPrecedence = new HashMap<>(NUM_PRECEDENCES);

  static {
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mPLUS, ADDITIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mMINUS, ADDITIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mSTAR, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mDIV, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mMOD, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mSTAR_STAR, EXPONENTIAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mLAND, AND_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mLOR, OR_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mBAND, BINARY_AND_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mBOR, BINARY_OR_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mBXOR, BINARY_XOR_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyElementTypes.COMPOSITE_LSHIFT_SIGN, SHIFT_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyElementTypes.COMPOSITE_RSHIFT_SIGN, SHIFT_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN, SHIFT_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mGT, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mGE, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mLT, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mLE, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mEQUAL, EQUALITY_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.kIN, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mNOT_EQUAL, EQUALITY_PRECEDENCE);
    s_binaryOperatorPrecedence.put(GroovyTokenTypes.mCOMPARE_TO, EQUALITY_PRECEDENCE);
  }

  public static int getPrecedence(GrExpression expression) {
    if (expression instanceof GrLiteral) {
      return LITERAL_PRECEDENCE;
    }
    if (expression instanceof GrReferenceExpression) {
      final GrReferenceExpression referenceExpression = (GrReferenceExpression)expression;
      return referenceExpression.getQualifierExpression() == null ? LITERAL_PRECEDENCE : METHOD_CALL_PRECEDENCE;
    }
    if (expression instanceof GrMethodCallExpression) {
      return METHOD_CALL_PRECEDENCE;
    }
    if (expression instanceof GrTypeCastExpression || expression instanceof GrNewExpression) {
      return TYPE_CAST_PRECEDENCE;
    }
    if (expression instanceof GrUnaryExpression) {
      return ((GrUnaryExpression)expression).isPostfix() ? POSTFIX_PRECEDENCE : PREFIX_PRECEDENCE;
    }
    if (expression instanceof GrBinaryExpression) {
      final IElementType sign = ((GrBinaryExpression)expression).getOperationTokenType();
      return precedenceForBinaryOperator(sign);
    }
    if (expression instanceof GrConditionalExpression) {
      return CONDITIONAL_PRECEDENCE;
    }
    if (expression instanceof GrAssignmentExpression) {
      return ASSIGNMENT_PRECEDENCE;
    }
    if (expression instanceof GrParenthesizedExpression) {
      return PARENTHESIZED_PRECEDENCE;
    }
    if (expression instanceof GrInstanceOfExpression) {
      return INSTANCEOF_PRECEDENCE;
    }
    return -1;
  }

  private static int precedenceForBinaryOperator(@NotNull IElementType sign) {
    return s_binaryOperatorPrecedence.get(sign);
  }
}
