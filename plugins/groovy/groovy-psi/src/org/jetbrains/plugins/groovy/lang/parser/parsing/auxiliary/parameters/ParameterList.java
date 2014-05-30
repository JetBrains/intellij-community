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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov, ilyas
 * @date: 26.03.2007
 */
public class ParameterList {

  /**
   * @param builder Given builder
   * @param ending  ending:  -> or ) in various cases
   */
  public static void parse(PsiBuilder builder, IElementType ending, GroovyParser parser) {
    if (builder.getTokenType() == GroovyTokenTypes.mRPAREN && GroovyTokenTypes.mRPAREN.equals(ending)) {
      PsiBuilder.Marker marker = builder.mark();
      marker.done(GroovyElementTypes.PARAMETERS_LIST);
      return;
    }

    PsiBuilder.Marker marker = builder.mark();


    if (!ParameterDeclaration.parseSimpleParameter(builder, parser)) {
      marker.rollbackTo();
      marker = builder.mark();
      marker.done(GroovyElementTypes.PARAMETERS_LIST);
      return;
    }

    while (!builder.eof() &&
            GroovyTokenTypes.mCOMMA.equals(builder.getTokenType())) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA);
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      if (!ParameterDeclaration.parseSimpleParameter(builder, parser)) {
        builder.error(GroovyBundle.message("param.expected"));
      }
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if ((ending.equals(GroovyTokenTypes.mCLOSABLE_BLOCK_OP) &&
            GroovyTokenTypes.mCLOSABLE_BLOCK_OP.equals(builder.getTokenType()))
            || ending.equals(GroovyTokenTypes.mRPAREN)) {
      marker.done(GroovyElementTypes.PARAMETERS_LIST);
    } else {
      marker.rollbackTo();
      marker = builder.mark();
      marker.done(GroovyElementTypes.PARAMETERS_LIST);
    }

  }
}
