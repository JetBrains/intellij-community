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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class OpenOrClosableBlock implements GroovyElementTypes {

  public static boolean parseBlockStatement(PsiBuilder builder, GroovyParser parser) {
    final PsiBuilder.Marker marker = builder.mark();
    final boolean result = parseOpenBlock(builder, parser);
    if (result) {
      marker.done(BLOCK_STATEMENT);
    } else {
      marker.drop();
    }
    return result;
  }

  public static boolean parseOpenBlock(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mLCURLY)) {
      marker.drop();
      return false;
    }
    ParserUtils.getToken(builder, mNLS);
    parser.parseBlockBody(builder);
    while (!builder.eof() &&
        !mRCURLY.equals(builder.getTokenType())) {
      builder.error(GroovyBundle.message("expression.expected"));
      builder.advanceLexer();
    }
    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));
    marker.done(OPEN_BLOCK);
    return true;
  }


  public static IElementType parseClosableBlock(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mLCURLY)) {
      marker.drop();
      return WRONGWAY;
    }
    ParserUtils.getToken(builder, mNLS);
    closableBlockParamsOpt(builder, parser);
    parser.parseBlockBody(builder);
    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));
    marker.done(CLOSABLE_BLOCK);
    return CLOSABLE_BLOCK;
  }


  private static void closableBlockParamsOpt(PsiBuilder builder, GroovyParser parser) {
    ParameterList.parse(builder, mCLOSABLE_BLOCK_OP, parser);
    ParserUtils.getToken(builder, mNLS);
    ParserUtils.getToken(builder, mCLOSABLE_BLOCK_OP);
  }

}
