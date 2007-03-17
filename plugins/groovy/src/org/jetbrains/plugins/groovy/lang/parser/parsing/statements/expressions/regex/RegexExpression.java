package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.regex;

import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.relational.EqualityExpression;
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
    boolean flag = false;

    if (!result.equals(WRONGWAY)) {
      while (ParserUtils.getToken(builder, REGEX_DO) && !result.equals(WRONGWAY)) {
        flag = true;
        ParserUtils.getToken(builder, mNLS);
        result = EqualityExpression.parse(builder);
        if (result.equals(WRONGWAY)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
      }
    }

    if (flag) {
      marker.done(REGEX_EXPRESSION);
      return REGEX_EXPRESSION;
    } else {
      marker.drop();
    }

    return result;

  }

}