package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class PowerExpressionNotPlusMinus implements GroovyElementTypes {

  /*
    public static GroovyElementType parse(PsiBuilder builder) {

      PsiBuilder.Marker marker = builder.mark();
      GroovyElementType result = UnaryExpressionNotPlusMinus.parse(builder);
      boolean flag = false;

      if (!result.equals(WRONGWAY)) {
        while (ParserUtils.getToken(builder, mSTAR_STAR) && !result.equals(WRONGWAY)) {
          flag = true;
          ParserUtils.getToken(builder, mNLS);
          result = UnaryExpression.parse(builder);
          if (result.equals(WRONGWAY)) {
            builder.error(GroovyBundle.message("expression.expected"));
          }
        }
      }

      if (flag) {
        marker.done(POWER_EXPRESSION_SIMPLE);
        return POWER_EXPRESSION_SIMPLE;
      } else {
        marker.drop();
      }

      return result;

    }
  */
  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    GroovyElementType result = UnaryExpressionNotPlusMinus.parse(builder);

    if (!result.equals(WRONGWAY)) {
      if (ParserUtils.getToken(builder, mSTAR_STAR)) {
        ParserUtils.getToken(builder, mNLS);
        result = UnaryExpression.parse(builder);
        if (result.equals(WRONGWAY)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(POWER_EXPRESSION_SIMPLE);
        result = POWER_EXPRESSION_SIMPLE;
        if (mSTAR_STAR.equals(builder.getTokenType())) {
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
    ParserUtils.getToken(builder, mSTAR_STAR);
    ParserUtils.getToken(builder, mNLS);
    GroovyElementType result = UnaryExpression.parse(builder);
    if (result.equals(WRONGWAY)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(POWER_EXPRESSION_SIMPLE);
    if (mSTAR_STAR.equals(builder.getTokenType())) {
      subParse(builder, newMarker);
    } else {
      newMarker.drop();
    }
    return POWER_EXPRESSION_SIMPLE;
  }


}

