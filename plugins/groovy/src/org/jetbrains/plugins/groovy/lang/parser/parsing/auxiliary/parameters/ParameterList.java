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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov, ilyas
 * @date: 26.03.2007
 */
public class ParameterList implements GroovyElementTypes {

  /**
   * @param builder Given builder
   * @param ending  ending:  -> or ) in various cases
   */
  public static void parse(PsiBuilder builder, IElementType ending, GroovyParser parser) {
    if (builder.getTokenType() == mRPAREN && mRPAREN.equals(ending)) {
      PsiBuilder.Marker marker = builder.mark();
      marker.done(PARAMETERS_LIST);
      return;
    }

    PsiBuilder.Marker marker = builder.mark();


    if (!ParameterDeclaration.parseSimpleParameter(builder, parser)) {
      marker.rollbackTo();
      marker = builder.mark();
      marker.done(PARAMETERS_LIST);
      return;
    }

    while (!builder.eof() &&
            mCOMMA.equals(builder.getTokenType())) {
      ParserUtils.getToken(builder, mCOMMA);
      ParserUtils.getToken(builder, mNLS);
      if (!ParameterDeclaration.parseSimpleParameter(builder, parser)) {
        builder.error(GroovyBundle.message("param.expected"));
      }
    }

    ParserUtils.getToken(builder, mNLS);

    if ((ending.equals(mCLOSABLE_BLOCK_OP) &&
            mCLOSABLE_BLOCK_OP.equals(builder.getTokenType()))
            || ending.equals(mRPAREN)) {
      marker.done(PARAMETERS_LIST);
    } else {
      marker.rollbackTo();
      marker = builder.mark();
      marker.done(PARAMETERS_LIST);
    }

  }
}
