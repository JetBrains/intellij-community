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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.typeDef;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class AnnotationDefinition implements GroovyElementTypes {
  public static boolean parseAnnotationDefinition(PsiBuilder builder, GroovyParser parser) {
    if (!ParserUtils.getToken(builder, mAT)) {
      return false;
    }

    if (!ParserUtils.getToken(builder, kINTERFACE)) {
      return false;
    }

    if (!ParserUtils.getToken(builder, mIDENT)) {
      builder.error(GroovyBundle.message("annotation.definition.qualified.name.expected"));
      return false;
    }

    PsiBuilder.Marker abMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY, GroovyBundle.message("lcurly.expected"))) {
      abMarker.rollbackTo();
      return false;
    }

    Separators.parse(builder);

    while (!builder.eof() && builder.getTokenType() != mRCURLY) {
      if (!parseAnnotationMember(builder, parser)) builder.advanceLexer();
      Separators.parse(builder);
    }

    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

    abMarker.done(CLASS_BODY);
    return true;
  }

  private static boolean parseAnnotationMember(PsiBuilder builder, GroovyParser parser) {
    //type definition
    PsiBuilder.Marker typeDeclStartMarker = builder.mark();

    if (TypeDefinition.parse(builder, parser)) {
      typeDeclStartMarker.drop();
      return true;
    }

    typeDeclStartMarker.rollbackTo();

    PsiBuilder.Marker declMarker = builder.mark();

    if (Declaration.parse(builder, true, true, parser)) {
      declMarker.drop();
      return true;
    }

    declMarker.rollbackTo();
    return false;
  }
}
