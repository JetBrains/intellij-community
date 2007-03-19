package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.relational.RelationalExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Ilya.Sergey
 */
public class ShiftExpression implements GroovyElementTypes {

  private static TokenSet SHIFTS = TokenSet.create(
          mSL,
          mSR,
          mBSR,
          mRANGE_EXCLUSIVE,
          mRANGE_INCLUSIVE
  );

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    GroovyElementType result = AdditiveExpression.parse(builder);

    if (!result.equals(WRONGWAY)) {
      if (ParserUtils.getToken(builder, SHIFTS)) {
        ParserUtils.getToken(builder, mNLS);
        result = AdditiveExpression.parse(builder);
        if (result.equals(WRONGWAY)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(SHIFT_EXPRESSION);
        result = SHIFT_EXPRESSION;
        if (SHIFTS.contains(builder.getTokenType())) {
          subParse(builder, newMarker);
        } else {
          newMarker.drop();
        }
      } else {
        marker.drop();
      }
    } else {
      marker.drop();
    }
    return result;
  }

  private static GroovyElementType subParse(PsiBuilder builder, PsiBuilder.Marker marker) {
    ParserUtils.getToken(builder, SHIFTS);
    ParserUtils.getToken(builder, mNLS);
    GroovyElementType result = AdditiveExpression.parse(builder);
    if (result.equals(WRONGWAY)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(SHIFT_EXPRESSION);
    if (SHIFTS.contains(builder.getTokenType())) {
      subParse(builder, newMarker);
    } else {
      newMarker.drop();
    }
    return SHIFT_EXPRESSION;
  }

}