package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.regex;

import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.relational.EqualityExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.MultiplicativeExpression;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Ilya.Sergey
 */
public class RegexExpression implements GroovyElementTypes {

  private static TokenSet REGEX_DO = TokenSet.create(
          mREGEX_FIND,
          mREGEX_MATCH
  );

    public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    GroovyElementType result = EqualityExpression.parse(builder);

    if (!result.equals(WRONGWAY)) {
      if (ParserUtils.getToken(builder, REGEX_DO)) {
        ParserUtils.getToken(builder, mNLS);
        result = EqualityExpression.parse(builder);
        if (result.equals(WRONGWAY)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(REGEX_EXPRESSION);
        result = REGEX_EXPRESSION;
        if (REGEX_DO.contains(builder.getTokenType())) {
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
    ParserUtils.getToken(builder, REGEX_DO);
    ParserUtils.getToken(builder, mNLS);
    GroovyElementType result = EqualityExpression.parse(builder);
    if (result.equals(WRONGWAY)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(REGEX_EXPRESSION);
    if (REGEX_DO.contains(builder.getTokenType())) {
      subParse(builder, newMarker);
    } else {
      newMarker.drop();
    }
    return REGEX_EXPRESSION;
  }

}