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
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterDeclaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;


/**
 * @author ilyas
 */
public class TryCatchStatement implements GroovyElementTypes {


  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, kTRY);
    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, mNLS);
    GroovyElementType result = WRONGWAY;
    if (mLCURLY.equals(builder.getTokenType())) {
      result = OpenOrClosableBlock.parseOpenBlock(builder);
    }
    if (result.equals(WRONGWAY)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("expression.expected"));
      marker.done(TRY_BLOCK_STATEMENT);
      return TRY_BLOCK_STATEMENT;
    }
    warn.drop();

    if (!ParserUtils.lookAhead(builder, kCATCH) &&
            !ParserUtils.lookAhead(builder, kFINALLY) &&
            !ParserUtils.lookAhead(builder, mNLS, kCATCH) &&
            !ParserUtils.lookAhead(builder, mNLS, kFINALLY)) {
      builder.error(GroovyBundle.message("catch.or.finally.expected"));
      marker.done(TRY_BLOCK_STATEMENT);
      return TRY_BLOCK_STATEMENT;
    }
    ParserUtils.getToken(builder, mNLS);

    if (kCATCH.equals(builder.getTokenType())) {
      parseHandlers(builder);
    }

    if (ParserUtils.lookAhead(builder, mNLS, kFINALLY)) {
      ParserUtils.getToken(builder, mNLS);
    }
    if (kFINALLY.equals(builder.getTokenType())) {
      PsiBuilder.Marker finallyMarker = builder.mark();
      warn = builder.mark();
      ParserUtils.getToken(builder, kFINALLY);
      ParserUtils.getToken(builder, mNLS);
      result = WRONGWAY;
      if (mLCURLY.equals(builder.getTokenType())) {
        result = OpenOrClosableBlock.parseOpenBlock(builder);
      }
      if (result.equals(WRONGWAY)) {
        finallyMarker.drop();
        warn.rollbackTo();
        builder.error(GroovyBundle.message("expression.expected"));
      } else {
        warn.drop();
        finallyMarker.done(FINALLY_CLAUSE);
      }
    }
    marker.done(TRY_BLOCK_STATEMENT);
    return TRY_BLOCK_STATEMENT;
  }

  /**
   * Parse exception handlers
   *
   * @param builder
   */
  private static void parseHandlers(PsiBuilder builder) {
    PsiBuilder.Marker catchMarker = builder.mark();
    ParserUtils.getToken(builder, kCATCH);
    if (!ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"))) {
      catchMarker.drop();
      return;
    }

    if (ParameterDeclaration.parse(builder, mRPAREN).equals(WRONGWAY)) {
      builder.error(GroovyBundle.message("param.expected"));
//      catchMarker.drop();
//      return;
    }

    if (ParserUtils.lookAhead(builder, mNLS, mRPAREN)) {
      ParserUtils.getToken(builder, mNLS);
    }
    if (!ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"))) {
      catchMarker.done(CATCH_CLAUSE);
      return;
    }

    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, mNLS);
    GroovyElementType result = WRONGWAY;
    if (mLCURLY.equals(builder.getTokenType())) {
      result = OpenOrClosableBlock.parseOpenBlock(builder);
    }
    if (result.equals(WRONGWAY)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("expression.expected"));
    } else {
      warn.drop();
    }

    catchMarker.done(CATCH_CLAUSE);

    if (ParserUtils.lookAhead(builder, mNLS, kCATCH) ||
            ParserUtils.lookAhead(builder, kCATCH)) {
      ParserUtils.getToken(builder, mNLS);
      parseHandlers(builder);
    }
  }

}
