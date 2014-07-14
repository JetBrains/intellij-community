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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.BinaryExpression;

/**
 * @author ilyas
 */
public class ShiftExpression {

  private static final TokenSet RANGES = TokenSet.create(
    GroovyTokenTypes.mRANGE_EXCLUSIVE,
    GroovyTokenTypes.mRANGE_INCLUSIVE
  );

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {


    PsiBuilder.Marker marker = builder.mark();
    if (BinaryExpression.ADDITIVE.parseBinary(builder, parser)) {
      GroovyElementType shiftOrRange = isRangeOrShift(builder);
      if (!shiftOrRange.equals(GroovyElementTypes.WRONGWAY)) {
        if (ParserUtils.getToken(builder, RANGES) ||
                getCompositeSign(builder)) {
          ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
          if (!BinaryExpression.ADDITIVE.parseBinary(builder, parser)) {
            builder.error(GroovyBundle.message("expression.expected"));
          }
          PsiBuilder.Marker newMarker = marker.precede();
          marker.done(shiftOrRange);
          shiftOrRange = isRangeOrShift(builder);
          if (RANGES.contains(builder.getTokenType()) ||
                  getCompositeSign(builder)) {
            subParse(builder, newMarker, shiftOrRange, parser);
          } else {
            newMarker.drop();
          }
        } else {
          marker.drop();
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

  /*
   * For composite shift operators like >>>
   */
  private static boolean getCompositeSign(PsiBuilder builder) {
    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mGT, GroovyTokenTypes.mGT, GroovyTokenTypes.mGT)) {
      PsiBuilder.Marker marker = builder.mark();
      for (int i = 0; i < 3; i++) {
        builder.getTokenText(); //todo[peter] remove look-ahead assertion
        builder.advanceLexer();
      }
      marker.done(GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN);
      return true;
    }
    else if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mLT, GroovyTokenTypes.mLT)) {
      PsiBuilder.Marker marker = builder.mark();
      for (int i = 0; i < 2; i++) {
        builder.getTokenText(); //todo[peter] remove look-ahead assertion
        builder.advanceLexer();
      }
      marker.done(GroovyElementTypes.COMPOSITE_LSHIFT_SIGN);
      return true;
    }
    else if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mGT, GroovyTokenTypes.mGT)) {
      PsiBuilder.Marker marker = builder.mark();
      for (int i = 0; i < 2; i++) {
        builder.getTokenText(); //todo[peter] remove look-ahead assertion
        builder.advanceLexer();
      }
      marker.done(GroovyElementTypes.COMPOSITE_RSHIFT_SIGN);
      return true;
    }
    else {
      return false;
    }
  }

  private static GroovyElementType isRangeOrShift(PsiBuilder builder) {
    if (RANGES.contains(builder.getTokenType())) return GroovyElementTypes.RANGE_EXPRESSION;
    PsiBuilder.Marker marker = builder.mark();
    if (getCompositeSign(builder)) {
      marker.rollbackTo();
      return GroovyElementTypes.SHIFT_EXPRESSION;
    } else {
      marker.rollbackTo();
    }
    return GroovyElementTypes.WRONGWAY;
  }

  private static void subParse(PsiBuilder builder, PsiBuilder.Marker marker, GroovyElementType shiftOrRange, GroovyParser parser) {
    ParserUtils.getToken(builder, RANGES);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (!BinaryExpression.ADDITIVE.parseBinary(builder, parser)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(shiftOrRange);
    GroovyElementType newShiftOrRange = isRangeOrShift(builder);
    if (RANGES.contains(builder.getTokenType()) ||
            getCompositeSign(builder)) {
      subParse(builder, newMarker, newShiftOrRange, parser);
    } else {
      newMarker.drop();
    }
  }

}
