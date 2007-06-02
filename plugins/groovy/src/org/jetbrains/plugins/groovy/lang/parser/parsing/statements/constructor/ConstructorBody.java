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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.constructor;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class ConstructorBody implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker cbMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      cbMarker.rollbackTo();
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    PsiBuilder.Marker constructorInvokationMarker = builder.mark();
    boolean b = parseExplicitConstructor(builder);
    if (b) {
      constructorInvokationMarker.done(EXPLICIT_CONSTRUCTOR);
    } else {
      constructorInvokationMarker.rollbackTo();
    }

    //explicit constructor invocation
    Separators.parse(builder);
    OpenOrClosableBlock.parseBlockBody(builder);

    if (!ParserUtils.getToken(builder, mRCURLY)) {
      builder.error(GroovyBundle.message("rcurly.expected"));
      cbMarker.rollbackTo();
      return CONSTRUCTOR_BODY_ERROR;
    }

//    cbMarker.done(CONSTRUCTOR_BODY);
//    return CONSTRUCTOR_BODY;

    cbMarker.done(OPEN_BLOCK);
    return OPEN_BLOCK;

//    ParserUtils.waitNextRCurly(builder);
//
//    if (!ParserUtils.getToken(builder, mRCURLY)) {
//      builder.error(GroovyBundle.message("rcurly.expected"));
//    }
//
//    cbMarker.done(METHOD_BODY);
//    return METHOD_BODY;
  }

  private static boolean parseExplicitConstructor(PsiBuilder builder) {
    TypeArguments.parse(builder);

    if (ParserUtils.getToken(builder, kTHIS) || ParserUtils.getToken(builder, kSUPER)) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, mLPAREN);
      ArgumentList.parse(builder, mRPAREN);
      ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"));
      marker.done(ARGUMENTS);
      return true;
    }

    return false;
  }
}

