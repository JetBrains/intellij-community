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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
* annotation	:	AT identifier (	LPAREN (	annotationArguments | ) RPAREN | )
*/


public class Annotation implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker annMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mAT)) {
      annMarker.rollbackTo();
      return WRONGWAY;
    }


    if (WRONGWAY.equals(ReferenceElement.parseReferenceElement(builder))) {
      annMarker.rollbackTo();
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mLPAREN)) {
      annMarker.done(ANNOTATION);
      return ANNOTATION;
    }

    AnnotationArguments.parse(builder);

    ParserUtils.getToken(builder, mNLS);

    if (!ParserUtils.getToken(builder, mRPAREN)) {
      annMarker.rollbackTo();
      return WRONGWAY;
    }

    annMarker.done(ANNOTATION);
    return ANNOTATION;
  }

  public static GroovyElementType parseAnnotationOptional(PsiBuilder builder) {
    PsiBuilder.Marker annOptMarker = builder.mark();

    boolean hasAnnotations = false;
    while (!WRONGWAY.equals(Annotation.parse(builder))) {
      ParserUtils.getToken(builder, mNLS);
      hasAnnotations = true;
    }

    if (hasAnnotations) {
      annOptMarker.done(MODIFIERS);
      return MODIFIERS;
    } else {
      annOptMarker.rollbackTo();
      return NONE;
    }
  }
}
