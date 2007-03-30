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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;

/**
 * @author: Dmitry.Krasilschikov, Ilya Sergey
 * @date: 26.03.2007
 */
public class ParameterDeclarationList implements GroovyElementTypes {

  /**
   * @param builder Given builder
   * @param ending  ending:  -> or ) in various cases
   * @return PARAMETERS_LIST
   */
  public static GroovyElementType parse(PsiBuilder builder, IElementType ending) {
    PsiBuilder.Marker pdlMarker = builder.mark();

    // TODO Do something with modifiers in variable definitions, not parameters case

    GroovyElementType result = ParameterDeclaration.parse(builder, ending);

    if (!PARAMETER.equals(result)) {
      pdlMarker.rollbackTo();
      return WRONGWAY;
    }

    while (!builder.eof() &&
            ParserUtils.getToken(builder, mCOMMA) &&
            result.equals(PARAMETER)) {
      ParserUtils.getToken(builder, mNLS);
      result = ParameterDeclaration.parse(builder, ending);
    }

    if ((ending.equals(mCLOSABLE_BLOCK_OP) &&
            mCLOSABLE_BLOCK_OP.equals(builder.getTokenType()))
            || ending.equals(mRPAREN)) {
      pdlMarker.done(PARAMETERS_LIST);
      return PARAMETERS_LIST;
    } else {
      pdlMarker.rollbackTo();
      return WRONGWAY;
    }

  }
}
