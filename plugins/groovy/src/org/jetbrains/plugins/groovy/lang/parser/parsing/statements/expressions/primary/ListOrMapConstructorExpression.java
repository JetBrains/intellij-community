package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class ListOrMapConstructorExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mLBRACK, GroovyBundle.message("lbrack.expected"))) {
      marker.drop();
      return WRONGWAY;
    }
    if (ParserUtils.getToken(builder, mRBRACK)) {
      marker.done(LIST_OR_MAP);
      return LIST_OR_MAP;
    } else if (ParserUtils.getToken(builder, mCOLON)) {
      ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
    } else if (!ArgumentList.parse(builder, mRBRACK).equals(WRONGWAY)) {
      ParserUtils.getToken(builder, mNLS);
      ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
    }

    marker.done(LIST_OR_MAP);
    return LIST_OR_MAP;
  }
}