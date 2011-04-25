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
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class EnumConstant implements GroovyElementTypes {
  public static boolean parseEnumConstant(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker ecMarker = builder.mark();
    ParserUtils.getToken(builder, mNLS);

    Annotation.parseAnnotationOptional(builder, parser);

    if (!ParserUtils.getToken(builder, mIDENT)) {
      ecMarker.rollbackTo();
      return false;
    }

    if (mLPAREN.equals(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, mLPAREN);
      ArgumentList.parseArgumentList(builder, mRPAREN, parser);

      ParserUtils.getToken(builder, mNLS);
      ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"));
      marker.done(ARGUMENTS);
    }

    if (builder.getTokenType() == mLCURLY) {
      final PsiBuilder.Marker enumInitializer = builder.mark();
      TypeDefinition.parseClassBody(builder, null, parser);
      enumInitializer.done(ENUM_CONSTANT_INITIALIZER);
    }

    ecMarker.done(ENUM_CONSTANT);
    return true;

  }

  public static void parseConstantList(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker enumConstantsMarker = builder.mark();

    if (!parseEnumConstant(builder, parser)) {
      return;
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      parseEnumConstant(builder, parser);
    }

    ParserUtils.getToken(builder, mCOMMA);

    enumConstantsMarker.done(ENUM_CONSTANTS);
  }
}
