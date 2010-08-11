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

package org.jetbrains.plugins.groovy.lang.parser.parsing.types;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.fail;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 28.03.2007
 */
public class TypeArgument implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder) {
    if (builder.getTokenType() == mQUESTION) {
      //wildcard
      PsiBuilder.Marker taMarker = builder.mark();
      ParserUtils.getToken(builder, mQUESTION);
      if (ParserUtils.getToken(builder, kSUPER) || ParserUtils.getToken(builder, kEXTENDS)) {
        ParserUtils.getToken(builder, mNLS);

        //todo: check for upper case type specification
        if (TypeSpec.parse(builder) == fail) {
          taMarker.rollbackTo();
          return false;
        }

        ParserUtils.getToken(builder, mNLS);
      }

      taMarker.done(TYPE_ARGUMENT);
      return true;
    }

    return TypeSpec.parse(builder) != fail;
  }
}
