package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ExpressionStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class CommandArguments implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    GroovyElementType result = ExpressionStatement.argParse(builder);
    if (! result.equals(WRONGWAY)){
      while (ParserUtils.lookAhead(builder, mCOMMA) && ! result.equals(WRONGWAY)) {
        ParserUtils.getToken(builder, mCOMMA);
        ParserUtils.getToken(builder, mNLS);
        result = ExpressionStatement.argParse(builder);
        if (result.equals(WRONGWAY)){
          builder.error(GroovyBundle.message("expression.expected"));
        }
      }
      marker.done(COMMAND_ARGUMENTS);
    } else {
      marker.drop();
    }

    return result;
  }

}