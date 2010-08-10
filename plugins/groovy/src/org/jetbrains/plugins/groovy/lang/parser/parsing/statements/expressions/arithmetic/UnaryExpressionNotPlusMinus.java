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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.fail;

/**
 * @author ilyas
 */
public class UnaryExpressionNotPlusMinus implements GroovyElementTypes {

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    if (builder.getTokenType() == mLPAREN) {
      final ReferenceElement.ReferenceElementResult result = parseTypeCast(builder);
      if (result != fail) {
        if (UnaryExpression.parse(builder, parser) || result == ReferenceElement.ReferenceElementResult.mustBeType) {
          marker.done(CAST_EXPRESSION);
          return true;
        } else {
          marker.rollbackTo();
          return PostfixExpression.parse(builder, parser);
        }
      } else {
        marker.drop();
        return PostfixExpression.parse(builder, parser);
      }
    } else {
      marker.drop();
      return PostfixExpression.parse(builder, parser);
    }
  }

  private static ReferenceElement.ReferenceElementResult parseTypeCast(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.rollbackTo();
      return fail;
    }
    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType()) ||
        mIDENT.equals(builder.getTokenType())) {
      final ReferenceElement.ReferenceElementResult result = TypeSpec.parseStrict(builder);
      if (result == fail) {
        marker.rollbackTo();
        return fail;
      }
      if (!ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"))) {
        marker.rollbackTo();
        return fail;
      }
      marker.drop();
      return result;
    }
    else {
      marker.rollbackTo();
      return fail;
    }
  }
}