package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Ilya.Sergey
 */
public class PostfixExpression implements GroovyElementTypes {


  private static TokenSet POSTFIXES = TokenSet.create(
          mINC,
          mDEC
  );

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    GroovyElementType result = PathExpression.parse(builder);
    if (!result.equals(WRONGWAY)) {
      subParse(builder, marker, result);
    } else {
      marker.drop();
    }
    return result;
  }

  private static GroovyElementType subParse(PsiBuilder builder,
                                            PsiBuilder.Marker marker,
                                            GroovyElementType result) {
    if (ParserUtils.getToken(builder, POSTFIXES)) {
      PsiBuilder.Marker newMarker = marker.precede();
      marker.done(POSTFIX_EXPRESSION);
      subParse(builder, newMarker, POSTFIX_EXPRESSION);
      return POSTFIX_EXPRESSION;
    } else {
      marker.drop();
      return result;
    }
  }


}
