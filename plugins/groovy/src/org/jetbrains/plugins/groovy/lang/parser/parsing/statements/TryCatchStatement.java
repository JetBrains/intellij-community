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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterDeclaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;


/**
 * @author ilyas
 */
public class TryCatchStatement implements GroovyElementTypes {


  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, kTRY);
    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, mNLS);
    if (!mLCURLY.equals(builder.getTokenType()) || !OpenOrClosableBlock.parseOpenBlock(builder, parser)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("expression.expected"));
      marker.done(TRY_BLOCK_STATEMENT);
      return true;
    }
    warn.drop();

    ParserUtils.getToken(builder, mNLS);
    if (!(builder.getTokenType() == kCATCH) &&
        !(builder.getTokenType() == kFINALLY)) {
      builder.error(GroovyBundle.message("catch.or.finally.expected"));
      marker.done(TRY_BLOCK_STATEMENT);
      return true;
    }

    if (kCATCH.equals(builder.getTokenType())) {
      parseHandlers(builder, parser);
    }

    if (kFINALLY.equals(builder.getTokenType()) || ParserUtils.lookAhead(builder, mNLS, kFINALLY)) {
      ParserUtils.getToken(builder, mNLS);
      PsiBuilder.Marker finallyMarker = builder.mark();
      warn = builder.mark();
      ParserUtils.getToken(builder, kFINALLY);
      ParserUtils.getToken(builder, mNLS);

      if (mLCURLY.equals(builder.getTokenType()) && OpenOrClosableBlock.parseOpenBlock(builder, parser)) {
        warn.drop();
        finallyMarker.done(FINALLY_CLAUSE);
      }
      else {
        finallyMarker.drop();
        warn.rollbackTo();
        builder.error(GroovyBundle.message("expression.expected"));
      }
    }
    marker.done(TRY_BLOCK_STATEMENT);
    return true;
  }

  /**
   * Parse exception handlers
   *
   * @param builder
   */
  private static void parseHandlers(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker catchMarker = builder.mark();
    ParserUtils.getToken(builder, kCATCH);
    if (!ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"))) {
      catchMarker.drop();
      return;
    }

    if (!ParameterDeclaration.parse(builder, parser)) {
      builder.error(GroovyBundle.message("param.expected"));
//      catchMarker.drop();
//      return;
    }

    ParserUtils.getToken(builder, mNLS);

    if (!ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"))) {
      catchMarker.done(CATCH_CLAUSE);
      return;
    }

    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, mNLS);
    if (!mLCURLY.equals(builder.getTokenType()) || !OpenOrClosableBlock.parseOpenBlock(builder, parser)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("expression.expected"));
    } else {
      warn.drop();
    }

    catchMarker.done(CATCH_CLAUSE);

    if (builder.getTokenType() == kCATCH ||
        ParserUtils.lookAhead(builder, mNLS, kCATCH)) {
      ParserUtils.getToken(builder, mNLS);
      parseHandlers(builder, parser);
    }
  }

}
