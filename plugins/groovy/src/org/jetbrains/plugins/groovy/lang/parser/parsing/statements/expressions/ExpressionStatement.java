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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.CommandArguments;

/**
 * Main classdef for any general expression parsing
 *
 * @author ilyas
 */
public class ExpressionStatement implements GroovyElementTypes {

  public static boolean parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    if (AssignmentExpression.parse(builder)) {
      if (!TokenSets.SEPARATORS.contains(builder.getTokenType())) {
        if (CommandArguments.parse(builder)) {
          marker.done(CALL_EXPRESSION);
        } else {
          marker.drop();
        }
      } else {
        marker.drop();
      }

      return true;
    } else {
      marker.drop();
      return false;
    }
  }

  /**
   * Use for parse expressions in Argument position
   *
   * @param builder - Given builder
   * @return type of parsing result
   */
  public static boolean argParse(PsiBuilder builder) {
    return AssignmentExpression.parse(builder);
  }


  /**
   * Checks whether first token of current statement is valid
   *
   * @param builder given Builder
   * @return true begin symbols are valid
   */
  public static boolean suspiciousExpressionStatementStart(PsiBuilder builder) {
    return TokenSets.SUSPICIOUS_EXPRESSION_STATEMENT_START_TOKEN_SET.contains(builder.getTokenType());
  }

  /**
   * Continues expressioin first checking
   *
   * @param builder given builder
   * @return true if it is expression really
   */
  public static boolean checkSuspiciousExpressionStatement(PsiBuilder builder) {
    // TODO realize me!
    return true;
  }

}
