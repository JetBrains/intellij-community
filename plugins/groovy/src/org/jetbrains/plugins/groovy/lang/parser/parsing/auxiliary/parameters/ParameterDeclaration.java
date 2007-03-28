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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.ParameterModifierOptional;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.VariableInitializer;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class ParameterDeclaration implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker pdMarker = builder.mark();

    if (WRONGWAY.equals(ParameterModifierOptional.parse(builder))) {
      pdMarker.rollbackTo();
      return WRONGWAY;
    }

    PsiBuilder.Marker checkMarker = builder.mark();

    GroovyElementType type = TypeSpec.parse(builder);
    if (!WRONGWAY.equals(type)) { //type was recognized
      ParserUtils.getToken(builder, mTRIPLE_DOT);

      if (!ParserUtils.getToken(builder, mIDENT)) { //if there is no identifier rollback to begin
        checkMarker.rollbackTo();

        if (!ParserUtils.getToken(builder, mIDENT)) { //parse identifier because suggestion about type was wrong
          pdMarker.rollbackTo();
          return WRONGWAY;
        }

        VariableInitializer.parse(builder);

        pdMarker.done(PARAMETER);
        return PARAMETER;
      } else { //parse typized parameter
        checkMarker.drop();
        VariableInitializer.parse(builder);

        pdMarker.done(PARAMETER);
        return PARAMETER;
      }
    } else {
      checkMarker.rollbackTo();

      if (!ParserUtils.getToken(builder, mIDENT)) { //parse parameter without type
        pdMarker.rollbackTo();
        return WRONGWAY;
      }

      VariableInitializer.parse(builder);

      pdMarker.done(PARAMETER);
      return PARAMETER;
    }
  }
}
