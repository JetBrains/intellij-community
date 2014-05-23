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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.BinaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class MultiplicativeExpression {

  private static final TokenSet MULT_DIV = TokenSet.create(
    GroovyTokenTypes.mSTAR,
    GroovyTokenTypes.mDIV,
    GroovyTokenTypes.mMOD
  );

  private static final TokenSet PREFIXES = TokenSet.create(
    GroovyTokenTypes.mPLUS,
    GroovyTokenTypes.mMINUS,
    GroovyTokenTypes.mINC,
    GroovyTokenTypes.mDEC,
    GroovyTokenTypes.mLNOT,
    GroovyTokenTypes.mBNOT
  );

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {

    PsiBuilder.Marker marker = builder.mark();
    if ((PREFIXES.contains(builder.getTokenType()))
        ? BinaryExpression.POWER.parseBinary(builder, parser)
        : PowerExpressionNotPlusMinus.parse(builder, parser)) {
      if (ParserUtils.getToken(builder, MULT_DIV)) {
        subParse(builder, parser, marker);
      }
      else {
        marker.drop();
      }
      return true;
    }
    else {
      marker.drop();
      return false;
    }
  }

  private static void subParse(PsiBuilder builder, GroovyParser parser, PsiBuilder.Marker marker) {
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (!BinaryExpression.POWER.parseBinary(builder, parser)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(GroovyElementTypes.MULTIPLICATIVE_EXPRESSION);
    if (MULT_DIV.contains(builder.getTokenType())) {
      ParserUtils.getToken(builder, MULT_DIV);
      subParse(builder, parser, newMarker);
    }
    else {
      newMarker.drop();
    }
  }
}
