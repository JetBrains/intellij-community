package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.relational;

import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.ShiftExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Ilya.Sergey
 */
public class RelationalExpression implements GroovyElementTypes {

  private static TokenSet RELATIONS = TokenSet.create(
          mLT,
          mGT,
          mLE,
          mGE,
          kIN
  );

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    GroovyElementType result = ShiftExpression.parse(builder);
    if (!result.equals(WRONGWAY)) {
      if (ParserUtils.getToken(builder, RELATIONS)) {
        result = RELATIONAL_EXPRESSION;
        ParserUtils.getToken(builder, mNLS);
        ShiftExpression.parse(builder);
        marker.done(RELATIONAL_EXPRESSION);

        // TODO add "instanceof" and "as" parsing

      } else {
        marker.drop();
      }
    } else {
      marker.drop();
    }

    return result;
  }

}