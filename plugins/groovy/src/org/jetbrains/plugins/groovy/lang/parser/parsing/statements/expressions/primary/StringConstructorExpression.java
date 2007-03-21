package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Identifier;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.*;

/**
 * @author Ilya.Sergey
 */
public class StringConstructorExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    Marker sMarker = builder.mark();
    if (ParserUtils.getToken(builder, mGSTRING_SINGLE_BEGIN)) {
      GroovyElementType result = stringConstructorValuePart(builder);
      if (result.equals(WRONGWAY)) {
        builder.error(GroovyBundle.message("identifier.or.block.expected"));
        sMarker.done(GSTRING);
        return GSTRING;
      } else {
        while (ParserUtils.getToken(builder, mGSTRING_SINGLE_CONTENT) && !result.equals(WRONGWAY)) {
          result = stringConstructorValuePart(builder);
        }
        ParserUtils.getToken(builder, mGSTRING_SINGLE_END, GroovyBundle.message("string.end.expected"));
        sMarker.done(GSTRING);
        return GSTRING;
      }
    } else {
      sMarker.drop();
      return WRONGWAY;
    }
  }

  /**
   * Parses heredoc's content in GString
   *
   * @param builder given builder
   * @return nothing
   */
  private static GroovyElementType stringConstructorValuePart(PsiBuilder builder) {
    ParserUtils.getToken(builder, mSTAR);
    if (mIDENT.equals(builder.getTokenType())) {
      PathExpression.parse(builder);
      return IDENTIFIER;
    }
    return WRONGWAY;
  }

}