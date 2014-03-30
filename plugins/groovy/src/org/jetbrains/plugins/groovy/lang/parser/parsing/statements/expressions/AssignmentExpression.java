/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.lang.PsiBuilder.Marker;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.TupleParse;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;


/**
 * @author ilyas
 */
public class AssignmentExpression implements GroovyElementTypes {

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    return parse(builder, parser, false);
  }

  public static boolean parse(PsiBuilder builder, GroovyParser parser, boolean comExprAllowed) {
    Marker marker = builder.mark();
    final boolean isTuple = ParserUtils.lookAhead(builder, mLPAREN, mIDENT, mCOMMA);
    if (parseSide(builder, parser, isTuple,comExprAllowed)) {
      if (ParserUtils.getToken(builder, TokenSets.ASSIGNMENTS)) {
        ParserUtils.getToken(builder, mNLS);
        if (!parse(builder, parser, comExprAllowed)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        marker.done(ASSIGNMENT_EXPRESSION);
      }
      else {
        if (isTuple) {
          builder.error(GroovyBundle.message("assign.expected"));
        }
        marker.drop();
      }
      return true;
    }
    else {
      marker.drop();
      return false;
    }
  }

  private static boolean parseSide(PsiBuilder builder, GroovyParser parser, boolean tuple, boolean comExprAllowed) {
    if (tuple) {
      return TupleParse.parseTupleForAssignment(builder);
    }

    if (comExprAllowed) {
      Marker marker = builder.mark();
      final ExpressionStatement.Result result = ExpressionStatement.parse(builder, parser);
      switch (result) {
        case EXPR_STATEMENT:
          marker.drop();
          return true;
        case EXPRESSION:
          ConditionalExpression.parseAfterCondition(builder, parser, marker);
          return true;
        case WRONG_WAY:
          marker.rollbackTo();
      }
    }
    return ConditionalExpression.parse(builder, parser);
  }
}
