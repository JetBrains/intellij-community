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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import com.intellij.lang.LighterASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.CommandArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.PrimaryExpression;

/**
 * Main classdef for any general expression parsing
 *
 * @author ilyas
 */
public class ExpressionStatement implements GroovyElementTypes {

  @Nullable
  private static IElementType parseExpressionStatement(PsiBuilder builder, GroovyParser parser) {
    final LighterASTNode firstDoneMarker = builder.getLatestDoneMarker();
    PsiBuilder.Marker marker = builder.mark();
    if (AssignmentExpression.parse(builder, parser) &&
        !TokenSets.SEPARATORS.contains(builder.getTokenType()) &&
        CommandArguments.parse(builder, parser)) {
      marker.done(CALL_EXPRESSION);
      return CALL_EXPRESSION;
    }
    marker.drop();
    final LighterASTNode latestDoneMarker = builder.getLatestDoneMarker();
    return latestDoneMarker != null && firstDoneMarker != latestDoneMarker ? latestDoneMarker.getTokenType() : WRONGWAY;
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
    builder.setDebugMode(true);
    PsiBuilder.Marker marker = builder.mark();

    final IElementType result = parseExpressionStatement(builder, parser);
    if (result != CALL_EXPRESSION) {
      marker.drop();
      return result != null;
    }

    while (true) {
      if (PathExpression.namePartParse(builder, parser) != REFERENCE_EXPRESSION) {
        marker.drop();
        break;
      }
      final PsiBuilder.Marker exprStatement = marker.precede();
      marker.done(REFERENCE_EXPRESSION);

      if (builder.getTokenType() == mLPAREN) {
        PrimaryExpression.methodCallArgsParse(builder, parser);
        exprStatement.done(PATH_METHOD_CALL);
      }
      else if (CommandArguments.parse(builder, parser)) {
        exprStatement.done(CALL_EXPRESSION);
      }
      else {
        exprStatement.drop();
        builder.error(GroovyBundle.message("expression.expected"));
        break;
      }

      marker = exprStatement.precede();
    }

    return true;
  }
}
