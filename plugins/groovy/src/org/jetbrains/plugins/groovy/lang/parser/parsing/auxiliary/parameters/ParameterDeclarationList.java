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
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov, ilyas
 * @date: 26.03.2007
 */
public class ParameterDeclarationList implements GroovyElementTypes {

  /**
   * @param builder Given builder
   * @param ending  ending:  -> or ) in various cases
   * @return PARAMETERS_LIST
   */
  public static GroovyElementType parse(PsiBuilder builder, IElementType ending) {
//    if (ParserUtils.lookAhead(builder, mRPAREN)) return NONE;

    if (builder.getTokenType() == mRPAREN && mRPAREN.equals(ending)) {
      PsiBuilder.Marker pdlMarker = builder.mark();
      pdlMarker.done(PARAMETERS_LIST);
      return NONE;
    }

    PsiBuilder.Marker pdlMarker = builder.mark();

    GroovyElementType result = ParameterDeclaration.parse(builder, ending);

    if (!PARAMETER.equals(result)) {
      pdlMarker.rollbackTo();
      pdlMarker = builder.mark();
      pdlMarker.done(PARAMETERS_LIST);
      return WRONGWAY;
    }

    while (!builder.eof() &&
            result.equals(PARAMETER) &&
            mCOMMA.equals(builder.getTokenType())) {
      PsiBuilder.Marker rb = builder.mark();
      ParserUtils.getToken(builder, mCOMMA);
      ParserUtils.getToken(builder, mNLS);
      result = ParameterDeclaration.parse(builder, ending);
      if (result.equals(PARAMETER)) {
        rb.drop();
      } else {
        rb.rollbackTo();
        if (mCOMMA.equals(builder.getTokenType())) {
          ParserUtils.getToken(builder, mCOMMA);
          builder.error(GroovyBundle.message("param.expected"));
        }
      }
    }

    if ((ending.equals(mCLOSABLE_BLOCK_OP) &&
            mCLOSABLE_BLOCK_OP.equals(builder.getTokenType()))
            || ending.equals(mRPAREN)) {
      pdlMarker.done(PARAMETERS_LIST);
      return PARAMETERS_LIST;
    } else {
      pdlMarker.rollbackTo();
      pdlMarker = builder.mark();
      pdlMarker.done(PARAMETERS_LIST);
      return WRONGWAY;
    }

  }
}
