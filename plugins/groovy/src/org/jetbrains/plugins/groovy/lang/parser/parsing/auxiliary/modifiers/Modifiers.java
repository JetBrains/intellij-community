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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * Modifiers ::= "def" nls
 *              | {modifier nls}+
 *              | {annotation nls}+
 */

public class Modifiers implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    boolean endsWithNewLine = false;
    PsiBuilder.Marker modifiersMarker = builder.mark();

    IElementType annotation = Annotation.parse(builder);
    IElementType modifier = Modifier.parse(builder);
    IElementType def = ParserUtils.getToken(builder, kDEF) ? kDEF : NONE;

    if (!(ANNOTATION.equals(annotation) || MODIFIERS.equals(modifier) || kDEF.equals(def))) {
      modifiersMarker.done(MODIFIERS);
      return WRONGWAY;
    }

    PsiBuilder.Marker newLineMarker = builder.mark();
    while (ANNOTATION.equals(annotation) || MODIFIERS.equals(modifier) || kDEF.equals(def)) {
      newLineMarker.drop();
      newLineMarker = builder.mark();
      endsWithNewLine = ParserUtils.getToken(builder, mNLS);

      annotation = Annotation.parse(builder);
      modifier = Modifier.parse(builder);
      def = ParserUtils.getToken(builder, kDEF) ? kDEF : NONE;
    }

    // Do not include last newline
    if (endsWithNewLine) {
      newLineMarker.rollbackTo();
    } else {
      newLineMarker.drop();
    }
    modifiersMarker.done(MODIFIERS);
    ParserUtils.getToken(builder, mNLS);
    return MODIFIERS;

  }
}