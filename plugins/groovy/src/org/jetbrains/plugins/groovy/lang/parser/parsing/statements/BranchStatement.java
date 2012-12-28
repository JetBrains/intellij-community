/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ConditionalExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class BranchStatement implements GroovyElementTypes {

  public static final TokenSet BRANCH_KEYWORDS = TokenSet.create(kRETURN,
          kBREAK,
          kCONTINUE,
          kTHROW,
          kRETURN,
          kASSERT);

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {

    if (kTHROW.equals(builder.getTokenType())) {
      throwParse(builder, parser);
      return true;
    }
    if (kASSERT.equals(builder.getTokenType())) {
      assertParse(builder, parser);
      return true;
    }
    if (kRETURN.equals(builder.getTokenType())) {
      returnParse(builder, parser);
      return true;
    }
    if (kBREAK.equals(builder.getTokenType()) ||
            kCONTINUE.equals(builder.getTokenType())) {
      breakOrContinueParse(builder);
      return true;
    }

    return false;
  }

  /**
   * return [Expression]
   *
   * @param builder
   * @return
   */
  private static void returnParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, kRETURN);
    AssignmentExpression.parse(builder, parser);
    marker.done(RETURN_STATEMENT);
  }

  /**
   * throw [Expression]
   *
   * @param builder
   * @return
   */
  private static void throwParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, kTHROW);
    if (!AssignmentExpression.parse(builder, parser)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    marker.done(THROW_STATEMENT);
  }

  /**
   * assert CondExpr [(COMMA | COLON) Expression]
   *
   * @param builder
   * @return
   */
  private static void assertParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, kASSERT);
    if (!ConditionalExpression.parse(builder, parser)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }

    if (ParserUtils.lookAhead(builder, mCOLON) || mCOMMA.equals(builder.getTokenType())) {
      builder.advanceLexer();
      ParserUtils.getToken(builder, mNLS);
      if (!AssignmentExpression.parse(builder, parser)) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
    }
    marker.done(ASSERT_STATEMENT);
  }

  /**
   * (BREAK | CONTINUE) ...
   *
   * @param builder
   * @return
   */
  private static void breakOrContinueParse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    GroovyElementType result = kBREAK.equals(builder.getTokenType()) ? BREAK_STATEMENT : CONTINUE_STATEMENT;

    builder.advanceLexer();

    ParserUtils.getToken(builder, mIDENT);

    marker.done(result);
  }


}
