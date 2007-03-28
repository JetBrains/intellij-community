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

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class ParameterDeclarationList implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
//    if (!ParserUtils.lookAhead(builder, kFINAL) && !ParserUtils.lookAhead(builder, kDEF) && !ParserUtils.lookAhead(builder, mAT)) {
//      builder.error(GroovyBundle.message("final.def.or.annotation.definition.expected"));
//      return WRONGWAY;
//    }

    PsiBuilder.Marker pdlMarker = builder.mark();

    if (WRONGWAY.equals(ParameterDeclaration.parse(builder))) {
      pdlMarker.rollbackTo();
      return WRONGWAY;
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      ParserUtils.getToken(builder, mNLS);

      if (WRONGWAY.equals(ParameterDeclaration.parse(builder))) {
        pdlMarker.rollbackTo();
        return WRONGWAY;
      }
    }

    pdlMarker.done(PARAMETERS_LIST);
    return PARAMETERS_LIST;
  }
}
