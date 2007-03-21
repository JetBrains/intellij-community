package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Ilya.Sergey
 */
public class MultiplicativeExpression implements GroovyElementTypes {

  private static TokenSet MULT_DIV = TokenSet.create(
          mSTAR,
          mDIV,
          mMOD
  );

  private static TokenSet PREFIXES = TokenSet.create(
          mPLUS,
          mMINUS,
          mINC,
          mDEC,
          mLNOT,
          mBNOT
  );

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    GroovyElementType result = (PREFIXES.contains(builder.getTokenType())) ?
            PowerExpression.parse(builder) : PowerExpressionNotPlusMinus.parse(builder);

    if (!result.equals(WRONGWAY)) {
      if (ParserUtils.getToken(builder, MULT_DIV)) {
        ParserUtils.getToken(builder, mNLS);
        result = PowerExpression.parse(builder);
        if (result.equals(WRONGWAY)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(MULTIPLICATIVE_EXPRESSION);
        result = MULTIPLICATIVE_EXPRESSION;
        if (MULT_DIV.contains(builder.getTokenType())) {
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
    ParserUtils.getToken(builder, MULT_DIV);
    ParserUtils.getToken(builder, mNLS);
    GroovyElementType result = PowerExpression.parse(builder);
    if (result.equals(WRONGWAY)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(MULTIPLICATIVE_EXPRESSION);
    if (MULT_DIV.contains(builder.getTokenType())) {
      subParse(builder, newMarker);
    } else {
      newMarker.drop();
    }
    return MULTIPLICATIVE_EXPRESSION;
  }

}