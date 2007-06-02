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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.ClassBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class EnumConstant implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker ecMarker = builder.mark();
    ParserUtils.getToken(builder, mNLS);

    Annotation.parseAnnotationOptional(builder);

    if (!ParserUtils.getToken(builder, mIDENT)) {
      ecMarker.rollbackTo();
      return WRONGWAY;
    }

    if (mLPAREN.equals(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, mLPAREN);
      ArgumentList.parse(builder, mRPAREN);

      ParserUtils.getToken(builder, mNLS);
      ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"));
      marker.done(ARGUMENTS);
    }

    if (builder.getTokenType() == mLCURLY) {
      ClassBlock.parse(builder, null);
    }

    ecMarker.done(ENUM_CONSTANT);
    return ENUM_CONSTANT;

  }
}
