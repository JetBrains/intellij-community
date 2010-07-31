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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.constructor;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class ConstructorBody implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker cbMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      cbMarker.rollbackTo();
      return false;
    }

    ParserUtils.getToken(builder, mNLS);

    PsiBuilder.Marker constructorInvokationMarker = builder.mark();
    if (parseExplicitConstructor(builder, parser)) {
      constructorInvokationMarker.done(EXPLICIT_CONSTRUCTOR);
    } else {
      constructorInvokationMarker.rollbackTo();
    }

    //explicit constructor invocation
    Separators.parse(builder);
    parser.parseBlockBody(builder);

    if (builder.getTokenType() != mRCURLY) {
      builder.error(GroovyBundle.message("rcurly.expected"));
    } else {
      builder.advanceLexer();
    }

    cbMarker.done(OPEN_BLOCK);
    return true;

  }

  private static boolean parseExplicitConstructor(PsiBuilder builder, GroovyParser parser) {
    TypeArguments.parse(builder);
    boolean result = false;
    if (ParserUtils.lookAhead(builder, kTHIS, mLPAREN)) {
      final PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, kTHIS);
      marker.done(THIS_REFERENCE_EXPRESSION);
      result = true;
    }
    if (ParserUtils.lookAhead(builder, kSUPER, mLPAREN)) {
      final PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, kSUPER);
      marker.done(SUPER_REFERENCE_EXPRESSION);
      result = true;
    }

    if (result) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, mLPAREN);
      ArgumentList.parseArgumentList(builder, mRPAREN, parser);
      ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"));
      marker.done(ARGUMENTS);
      return true;
    }

    return false;
  }
}

