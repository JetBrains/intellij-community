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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.CommandArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.UnaryExpressionNotPlusMinus;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.PrimaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Main classdef for any general expression parsing
 *
 * @author ilyas
 */
public class ExpressionStatement implements GroovyElementTypes {

  @Nullable
  private static IElementType parseExpressionStatement(PsiBuilder builder, GroovyParser parser) {
    if (checkForTypeCast(builder, parser)) return CAST_EXPRESSION;
    PsiBuilder.Marker marker = builder.mark();
    final PathExpression.Result result = PathExpression.parseForExprStatement(builder, parser);
    if (result != PathExpression.Result.WRONG_WAY &&
        !TokenSets.SEPARATORS.contains(builder.getTokenType()) &&
        !TokenSets.BINARY_OP_SET.contains(builder.getTokenType()) &&
        !TokenSets.POSTFIX_UNARY_OP_SET.contains(builder.getTokenType())) {
      if (result == PathExpression.Result.CALL_WITH_CLOSURE) {
        marker.drop();
        return PATH_METHOD_CALL;
      }
      else if (CommandArguments.parseCommandArguments(builder, parser)) {
        marker.done(CALL_EXPRESSION);
        return CALL_EXPRESSION;
      }
    }
    marker.drop();
    return WRONGWAY;
  }

  private static boolean checkForTypeCast(PsiBuilder builder, GroovyParser parser) {
    return UnaryExpressionNotPlusMinus.parse(builder, parser, false);
  }

  /**
   * Use for parse expressions in Argument position
   *
   * @param builder - Given builder
   * @return type of parsing result
   */
  public static boolean argParse(PsiBuilder builder, GroovyParser parser) {
    return AssignmentExpression.parse(builder, parser);
  }

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();

    final IElementType result = parseExpressionStatement(builder, parser);
    if (result != CALL_EXPRESSION && result != PATH_METHOD_CALL) {
      marker.drop();
      return result != WRONGWAY;
    }

    while (true) {
      if (PathExpression.namePartParse(builder, parser) != REFERENCE_EXPRESSION) {
        marker.drop();
        break;
      }
      PsiBuilder.Marker exprStatement = marker.precede();
      marker.done(REFERENCE_EXPRESSION);

      if (builder.getTokenType() == mLPAREN) {
        PrimaryExpression.methodCallArgsParse(builder, parser);
        exprStatement.done(PATH_METHOD_CALL);
      }
      else if (mLBRACK.equals(builder.getTokenType()) &&
               !ParserUtils.lookAhead(builder, mLBRACK, mCOLON) &&
               !ParserUtils.lookAhead(builder, mLBRACK, mNLS, mCOLON)) {
        PathExpression.indexPropertyArgsParse(builder, parser);
        exprStatement.done(PATH_INDEX_PROPERTY);
        if (mLPAREN.equals(builder.getTokenType())) {
          PrimaryExpression.methodCallArgsParse(builder, parser);
        }
        else if (mLCURLY.equals(builder.getTokenType())) {
          PsiBuilder.Marker argsMarker = builder.mark();
          argsMarker.done(ARGUMENTS);
        }
        while (mLCURLY.equals(builder.getTokenType())) {
          OpenOrClosableBlock.parseClosableBlock(builder, parser);
        }
        exprStatement = exprStatement.precede();
        exprStatement.done(PATH_METHOD_CALL);
      }
      else if (CommandArguments.parseCommandArguments(builder, parser)) {
        exprStatement.done(CALL_EXPRESSION);
      }
      else {
        exprStatement.drop();
        break;
      }

      marker = exprStatement.precede();
    }

    return true;
  }
}
