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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.constructor;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class ConstructorBody {
  public static void parseConstructorBody(PsiBuilder builder, GroovyParser parser) {
    assert builder.getTokenType() == GroovyTokenTypes.mLCURLY;
    if (parser.parseDeep()) {
      parseConstructorBodyDeep(builder, parser);
    } else {
      OpenOrClosableBlock.parseBlockShallow(builder, GroovyElementTypes.CONSTRUCTOR_BODY);
    }
  }

  public static void parseConstructorBodyDeep(PsiBuilder builder, GroovyParser parser) {
    assert builder.getTokenType() == GroovyTokenTypes.mLCURLY;
    PsiBuilder.Marker cbMarker = builder.mark();
    builder.advanceLexer();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    PsiBuilder.Marker constructorInvocationMarker = builder.mark();
    if (parseExplicitConstructor(builder, parser)) {
      constructorInvocationMarker.done(GroovyElementTypes.EXPLICIT_CONSTRUCTOR);
    } else {
      constructorInvocationMarker.rollbackTo();
    }

    //explicit constructor invocation
    Separators.parse(builder);
    parser.parseBlockBody(builder);

    if (builder.getTokenType() != GroovyTokenTypes.mRCURLY) {
      builder.error(GroovyBundle.message("rcurly.expected"));
    } else {
      builder.advanceLexer();
    }

    cbMarker.done(GroovyElementTypes.CONSTRUCTOR_BODY);
  }

  private static boolean parseExplicitConstructor(PsiBuilder builder, GroovyParser parser) {
    boolean result = false;
    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.kTHIS, GroovyTokenTypes.mLPAREN)) {
      final PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.kTHIS);
      marker.done(GroovyElementTypes.REFERENCE_EXPRESSION);
      result = true;
    }
    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.kSUPER, GroovyTokenTypes.mLPAREN)) {
      final PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.kSUPER);
      marker.done(GroovyElementTypes.REFERENCE_EXPRESSION);
      result = true;
    }

    if (result) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN);
      ArgumentList.parseArgumentList(builder, GroovyTokenTypes.mRPAREN, parser);
      ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"));
      marker.done(GroovyElementTypes.ARGUMENTS);
      return true;
    }

    return false;
  }
}

