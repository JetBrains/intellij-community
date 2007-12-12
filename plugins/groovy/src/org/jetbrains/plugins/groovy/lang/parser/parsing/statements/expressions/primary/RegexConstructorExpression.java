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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class RegexConstructorExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker sMarker = builder.mark();
    if (ParserUtils.getToken(builder, mREGEX_BEGIN)) {
      GroovyElementType result = regexConstructorValuePart(builder);
      if (result.equals(WRONGWAY)) {
        if (!ParserUtils.getToken(builder, mREGEX_END)) {
          builder.error(GroovyBundle.message("identifier.or.block.expected"));
        }
        sMarker.done(REGEX);
        return REGEX;
      } else {
        while (ParserUtils.getToken(builder, mREGEX_CONTENT) && !result.equals(WRONGWAY)) {
          result = regexConstructorValuePart(builder);
        }
        if (!ParserUtils.getToken(builder, mREGEX_END)) {
          builder.error(GroovyBundle.message("identifier.or.block.expected"));
        }
        sMarker.done(REGEX);
        return REGEX;
      }
    } else {
      sMarker.drop();
      return WRONGWAY;
    }
  }

  /**
   * Parses heredoc's content in GString
   *
   * @param builder given builder
   * @return nothing
   */
  private static GroovyElementType regexConstructorValuePart(PsiBuilder builder) {
    //ParserUtils.getToken(builder, mSTAR);
    if (mIDENT.equals(builder.getTokenType())) {
      PathExpression.parse(builder);
      return PATH_EXPRESSION;
    } else if (mLCURLY.equals(builder.getTokenType())) {
      OpenOrClosableBlock.parseClosableBlock(builder);
      return CLOSABLE_BLOCK;
    }
    return WRONGWAY;
  }

}