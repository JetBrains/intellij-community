// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
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
public class ShiftExpression {

  private static final TokenSet RANGES = TokenSet.create(
    GroovyTokenTypes.mRANGE_EXCLUSIVE,
    GroovyTokenTypes.mRANGE_INCLUSIVE
  );

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {


    PsiBuilder.Marker marker = builder.mark();
    if (BinaryExpression.ADDITIVE.parseBinary(builder, parser)) {
      IElementType shiftOrRange = isRangeOrShift(builder);
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

  private static IElementType isRangeOrShift(PsiBuilder builder) {
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

  private static void subParse(PsiBuilder builder, PsiBuilder.Marker marker, IElementType shiftOrRange, GroovyParser parser) {
    ParserUtils.getToken(builder, RANGES);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (!BinaryExpression.ADDITIVE.parseBinary(builder, parser)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(shiftOrRange);
    IElementType newShiftOrRange = isRangeOrShift(builder);
    if (RANGES.contains(builder.getTokenType()) ||
            getCompositeSign(builder)) {
      subParse(builder, newMarker, newShiftOrRange, parser);
    } else {
      newMarker.drop();
    }
  }

}
