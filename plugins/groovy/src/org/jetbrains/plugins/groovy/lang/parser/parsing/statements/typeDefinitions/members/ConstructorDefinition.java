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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.ThrowClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifier;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.constructor.ConstructorBody;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 23.03.2007
 */

public class ConstructorDefinition implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder, String className, GroovyParser parser) {
    if (className == null) return false;
    PsiBuilder.Marker constructorMarker = builder.mark();
    if (!parseModifiers(builder, parser)) {
      constructorMarker.rollbackTo();
      return false;
    }

    if (builder.getTokenType() != mIDENT || !className.equals(builder.getTokenText())) {
      builder.error(GroovyBundle.message("identifier.expected"));
      constructorMarker.rollbackTo();
      return false;
    } else {
      builder.advanceLexer();
    }

    if (!ParserUtils.getToken(builder, mLPAREN)) {
      builder.error(GroovyBundle.message("lparen.expected"));
    }

    ParameterList.parse(builder, mRPAREN, parser);

    ParserUtils.getToken(builder, mNLS);
    if (!ParserUtils.getToken(builder, mRPAREN)) {
      constructorMarker.rollbackTo();
      return false;
    }

    if (ParserUtils.lookAhead(builder, mNLS, kTHROWS) || ParserUtils.lookAhead(builder, mNLS, mLCURLY)) {
      ParserUtils.getToken(builder, mNLS);
    }

    ThrowClause.parse(builder);

    if (builder.getTokenType() == mLCURLY || ParserUtils.lookAhead(builder, mNLS, mLCURLY)) {
      ParserUtils.getToken(builder, mNLS);
      if (ConstructorBody.parse(builder, parser)) {
        constructorMarker.done(CONSTRUCTOR_DEFINITION);
        return true;
      }
    }

    constructorMarker.rollbackTo();
    return false;
  }

  private static boolean parseModifiers(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker modifiersMarker = builder.mark();

    do {
      if (kSTATIC.equals(builder.getTokenType())) {
        modifiersMarker.rollbackTo();
        return false;
      }
      ParserUtils.getToken(builder, mNLS);
    } while(Annotation.parse(builder, parser) || Modifier.parse(builder));

    modifiersMarker.done(MODIFIERS);
    return true;
  }

}