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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterDeclaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;


/**
 * @author ilyas
 */
public class TryCatchStatement {


  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.kTRY);
    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (!OpenOrClosableBlock.parseOpenBlock(builder, parser)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("lcurly.expected"));
      marker.drop();
      return true;
    }
    warn.drop();

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (!(builder.getTokenType() == GroovyTokenTypes.kCATCH) &&
        !(builder.getTokenType() == GroovyTokenTypes.kFINALLY)) {
      builder.error(GroovyBundle.message("catch.or.finally.expected"));
      marker.done(GroovyElementTypes.TRY_BLOCK_STATEMENT);
      return true;
    }

    if (GroovyTokenTypes.kCATCH.equals(builder.getTokenType())) {
      parseHandlers(builder, parser);
    }

    if (GroovyTokenTypes.kFINALLY.equals(builder.getTokenType()) || ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS,
                                                                                          GroovyTokenTypes.kFINALLY)) {
      parseFinally(builder, parser);
    }

    marker.done(GroovyElementTypes.TRY_BLOCK_STATEMENT);
    return true;
  }

  private static void parseFinally(PsiBuilder builder, GroovyParser parser) {
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    PsiBuilder.Marker finallyMarker = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.kFINALLY);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    PsiBuilder.Marker warn = builder.mark();
    if (GroovyTokenTypes.mLCURLY.equals(builder.getTokenType()) && OpenOrClosableBlock.parseOpenBlock(builder, parser)) {
      warn.drop();
    }
    else {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("lcurly.expected"));
    }
    finallyMarker.done(GroovyElementTypes.FINALLY_CLAUSE);
  }

  /**
   * Parse exception handlers
   *
   * @param builder
   */
  private static void parseHandlers(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker catchMarker = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.kCATCH);
    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN, GroovyBundle.message("lparen.expected"))) {
      catchMarker.done(GroovyElementTypes.CATCH_CLAUSE);
      return;
    }

    if (!ParameterDeclaration.parseCatchParameter(builder, parser)) {
      builder.error(GroovyBundle.message("param.expected"));
    }


    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"))) {
      catchMarker.done(GroovyElementTypes.CATCH_CLAUSE);
      return;
    }

    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (!GroovyTokenTypes.mLCURLY.equals(builder.getTokenType()) || !OpenOrClosableBlock.parseOpenBlock(builder, parser)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("lcurly.expected"));
    }
    else {
      warn.drop();
    }

    catchMarker.done(GroovyElementTypes.CATCH_CLAUSE);

    if (builder.getTokenType() == GroovyTokenTypes.kCATCH ||
        ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS, GroovyTokenTypes.kCATCH)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      parseHandlers(builder, parser);
    }
  }
}
