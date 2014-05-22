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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.regex;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.BinaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class RegexExpression {

  private static final TokenSet REGEX_DO = TokenSet.create(
    GroovyTokenTypes.mREGEX_FIND,
    GroovyTokenTypes.mREGEX_MATCH
  );

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {

    PsiBuilder.Marker marker = builder.mark();
    if (BinaryExpression.EQUALITY.parseBinary(builder, parser)) {
      IElementType type = builder.getTokenType();
      if (ParserUtils.getToken(builder, REGEX_DO)) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
        if (!BinaryExpression.EQUALITY.parseBinary(builder, parser)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(type == GroovyTokenTypes.mREGEX_FIND
                    ? GroovyElementTypes.REGEX_FIND_EXPRESSION
                    : GroovyElementTypes.REGEX_MATCH_EXPRESSION);
        if (REGEX_DO.contains(builder.getTokenType())) {
          subParse(builder, newMarker, parser);
        } else {
          newMarker.drop();
        }
      } else {
        marker.drop();
      }
      return true;
    } else {
      marker.drop();
      return false;
    }
  }

  private static void subParse(PsiBuilder builder, PsiBuilder.Marker marker, GroovyParser parser) {
    IElementType type = builder.getTokenType();
    ParserUtils.getToken(builder, REGEX_DO);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (!BinaryExpression.EQUALITY.parseBinary(builder, parser)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(type == GroovyTokenTypes.mREGEX_FIND ? GroovyElementTypes.REGEX_FIND_EXPRESSION : GroovyElementTypes.REGEX_MATCH_EXPRESSION);
    if (REGEX_DO.contains(builder.getTokenType())) {
      subParse(builder, newMarker, parser);
    } else {
      newMarker.drop();
    }
  }

}
