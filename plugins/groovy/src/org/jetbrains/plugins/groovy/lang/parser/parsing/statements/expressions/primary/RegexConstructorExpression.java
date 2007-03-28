package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class RegexConstructorExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker sMarker = builder.mark();
    if (ParserUtils.getToken(builder, mREGEX_BEGIN)) {
      GroovyElementType result = regexConstructorValuePart(builder);
      if (result.equals(WRONGWAY)) {
        builder.error(GroovyBundle.message("identifier.or.block.expected"));
        sMarker.done(REGEX);
        return REGEX;
      } else {
        while (ParserUtils.getToken(builder, mREGEX_CONTENT) && !result.equals(WRONGWAY)) {
          result = regexConstructorValuePart(builder);
        }
        ParserUtils.getToken(builder, mREGEX_END, GroovyBundle.message("regex.end.expected"));
        sMarker.done(REGEX);
        return REGEX;
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
  private static GroovyElementType regexConstructorValuePart(PsiBuilder builder) {
    //ParserUtils.getToken(builder, mSTAR);
    if (mIDENT.equals(builder.getTokenType())) {
      PathExpression.parse(builder);
      return PATH_EXPRESSION;
    } else if (mLCURLY.equals(builder.getTokenType())){
      OpenOrClosableBlock.parse(builder);
      return CLOSABLE_BLOCK;
    }
    return WRONGWAY;
  }

}