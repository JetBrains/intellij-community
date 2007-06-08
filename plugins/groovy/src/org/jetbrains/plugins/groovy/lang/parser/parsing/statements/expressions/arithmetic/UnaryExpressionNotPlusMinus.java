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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class UnaryExpressionNotPlusMinus implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    GroovyElementType result;
    PsiBuilder.Marker marker = builder.mark();
    if (builder.getTokenType() == mLPAREN) {
      if (!parseTypeCast(builder).equals(WRONGWAY)) {
        result = UnaryExpression.parse(builder);
        if (!result.equals(WRONGWAY)) {
          marker.done(CAST_EXPRESSION);
        } else {
          marker.rollbackTo();
          result = PostfixExpression.parse(builder);
        }
      } else {
        marker.drop();
        result = PostfixExpression.parse(builder);
      }
    } else {
      marker.drop();
      result = PostfixExpression.parse(builder);
    }
    return result;
  }

  private static GroovyElementType parseTypeCast(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.rollbackTo();
      return WRONGWAY;
    }
    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType()) ||
            mIDENT.equals(builder.getTokenType())) {
      if (TypeSpec.parseStrict(builder).equals(WRONGWAY)) {
        marker.rollbackTo();
        return WRONGWAY;
      }
      if (!ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"))) {
        marker.rollbackTo();
        return WRONGWAY;
      }
      marker.drop();
      return TYPE_CAST;
    } else {
      marker.rollbackTo();
      return WRONGWAY;
    }
  }

}