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

package org.jetbrains.plugins.groovy.lang.parser.parsing.types;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 28.03.2007
 */
public class TypeArgument implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker taMarker = builder.mark();
    //wildcard
    if (ParserUtils.getToken(builder, mQUESTION)) {
      if (ParserUtils.getToken(builder, kSUPER) || ParserUtils.getToken(builder, kEXTENDS)) {
        ParserUtils.getToken(builder, mNLS);

        //todo: check for upper case type specification
        if (WRONGWAY.equals(TypeSpec.parse(builder))) {
          taMarker.rollbackTo();
          return WRONGWAY;
        }

        ParserUtils.getToken(builder, mNLS);
      }

      taMarker.done(TYPE_ARGUMENT);
      return TYPE_ARGUMENT;
    }

    //typeSpec
    //todo: check for upper case type specification
    if (WRONGWAY.equals(TypeSpec.parse(builder))) {
//    if (WRONGWAY.equals(ReferenceElement.parseReferenceElement(builder))) {
      taMarker.rollbackTo();
      return WRONGWAY;
    } else {
      taMarker.done(TYPE_ARGUMENT);
      return TYPE_ARGUMENT;
    }
  }
}
