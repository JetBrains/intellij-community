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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
* annotation	:	AT identifier (	LPAREN (	annotationArguments | ) RPAREN | )
*/


public class Annotation {
  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    if (builder.getTokenType() != GroovyTokenTypes.mAT) {
      return false;
    }

    PsiBuilder.Marker annMarker = builder.mark();
    builder.advanceLexer();

    if (builder.getTokenType() == GroovyTokenTypes.kINTERFACE) {
      annMarker.rollbackTo();
      return false;
    }

    if (ReferenceElement.parse(builder, false, false, true, false, false) == ReferenceElement.ReferenceElementResult.FAIL) {
      builder.error("Annotation name expected");
      annMarker.drop();
      return false;
    }

    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS, GroovyTokenTypes.mLPAREN)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    }
    AnnotationArguments.parse(builder, parser);

    annMarker.done(GroovyElementTypes.ANNOTATION);
    return true;
  }
}
