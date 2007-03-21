package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class UnaryExpressionNotPlusMinus implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    GroovyElementType result;
    PsiBuilder.Marker marker = builder.mark();
    if (ParserUtils.lookAhead(builder, mLPAREN)) {
      if (!parseTypeCast(builder).equals(WRONGWAY)) {
        result = UnaryExpression.parse(builder);
        marker.done(CAST_EXPRESSION);
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
    ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"));
    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())) {
      PsiBuilder.Marker arrMarker = builder.mark();
      ParserUtils.getToken(builder, TokenSets.BUILT_IN_TYPE);
      if (mLBRACK.equals(builder.getTokenType())) {
        declarationBracketsParse(builder, arrMarker);
      } else {
        arrMarker.done(BUILT_IN_TYPE);
      }
      ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"));
      marker.drop();
      return TYPE_CAST;
    } else {
      marker.rollbackTo();
      return WRONGWAY;
    }
  }

  private static void declarationBracketsParse(PsiBuilder builder, PsiBuilder.Marker marker) {
    ParserUtils.getToken(builder, mLBRACK);
    ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(ARRAY_TYPE);
    if (mLBRACK.equals(builder.getTokenType())) {
      declarationBracketsParse(builder, newMarker);
    } else {
      newMarker.drop();
    }
  }

}