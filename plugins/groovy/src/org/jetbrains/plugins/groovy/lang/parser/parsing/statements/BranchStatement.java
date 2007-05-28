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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ConditionalExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class BranchStatement implements GroovyElementTypes {

  public static TokenSet BRANCH_KEYWORDS = TokenSet.create(kRETURN,
          kBREAK,
          kCONTINUE,
          kTHROW,
          kRETURN,
          kASSERT);

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    if (kTHROW.equals(builder.getTokenType())) {
      marker.done(throwParse(builder));
      return THROW_STATEMENT;
    }
    if (kASSERT.equals(builder.getTokenType())) {
      marker.done(assertParse(builder));
      return ASSERT_STATEMENT;
    }
    if (kRETURN.equals(builder.getTokenType())) {
      marker.done(returnParse(builder));
      return RETURN_STATEMENT;
    }
    if (kBREAK.equals(builder.getTokenType()) ||
            kCONTINUE.equals(builder.getTokenType())) {
      GroovyElementType result = breakOrContinueParse(builder);
      marker.done(result);
      return result;
    }

    marker.drop();
    return WRONGWAY;
  }

  /**
   * return [Expression]
   *
   * @param builder
   * @return
   */
  private static GroovyElementType returnParse(PsiBuilder builder) {
    ParserUtils.getToken(builder, kRETURN);
    AssignmentExpression.parse(builder);
    return RETURN_STATEMENT;
  }

  /**
   * throw [Expression]
   *
   * @param builder
   * @return
   */
  private static GroovyElementType throwParse(PsiBuilder builder) {
    ParserUtils.getToken(builder, kTHROW);
    if (AssignmentExpression.parse(builder).equals(WRONGWAY)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    return THROW_STATEMENT;
  }

  /**
   * assert CondExpr [(COMMA | COLON) Expression]
   *
   * @param builder
   * @return
   */
  private static GroovyElementType assertParse(PsiBuilder builder) {
    ParserUtils.getToken(builder, kASSERT);
    if (ConditionalExpression.parse(builder).equals(WRONGWAY)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    if (mCOLON.equals(builder.getTokenType()) ||
            mCOMMA.equals(builder.getTokenType())) {
      builder.advanceLexer();
      if (AssignmentExpression.parse(builder).equals(WRONGWAY)) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
    }
    return ASSERT_STATEMENT;
  }

  /**
   * (BREAK | CONTINUE) ...
   *
   * @param builder
   * @return
   */
  private static GroovyElementType breakOrContinueParse(PsiBuilder builder) {
    GroovyElementType result = kBREAK.equals(builder.getTokenType()) ?
            BREAK_STATEMENT : CONTINUE_STATEMENT;

    builder.advanceLexer();

    // TODO How does it works?
    ParserUtils.getToken(builder, mIDENT);

    return result;
  }


}
