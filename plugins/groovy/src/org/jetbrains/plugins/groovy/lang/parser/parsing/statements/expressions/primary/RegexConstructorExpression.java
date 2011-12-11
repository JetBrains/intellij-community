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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class RegexConstructorExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder, GroovyParser parser) {

    PsiBuilder.Marker sMarker = builder.mark();
    if (ParserUtils.getToken(builder, mREGEX_BEGIN)) {
      ParserUtils.getToken(builder, mREGEX_CONTENT);
      if (!parseInjection(builder, parser)) {
        if (!ParserUtils.getToken(builder, mREGEX_END)) {
          builder.error(GroovyBundle.message("regex.end.expected"));
        }
        sMarker.done(REGEX);
        return REGEX;
      }
      else {
        while (ParserUtils.getToken(builder, mREGEX_CONTENT)) {
          if (!parseInjection(builder, parser)) break;
        }
        if (!ParserUtils.getToken(builder, mREGEX_END)) {
          builder.error(GroovyBundle.message("regex.end.expected"));
        }
        sMarker.done(REGEX);
        return REGEX;
      }
    }
    else {
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
  private static boolean parseInjection(PsiBuilder builder, GroovyParser parser) {
    if (builder.getTokenType() != mDOLLAR) return false;

    final PsiBuilder.Marker injection = builder.mark();
    ParserUtils.getToken(builder, mDOLLAR);

    if (mIDENT.equals(builder.getTokenType())) {
      PathExpression.parse(builder, parser);
    }
    else if (mLCURLY.equals(builder.getTokenType())) {
      OpenOrClosableBlock.parseClosableBlock(builder, parser);
    }
    else {
      injection.drop();
      return false;
    }

    injection.done(GSTRING_INJECTION);
    return true;
  }
}