package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class ListOrMapConstructorExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mLBRACK, GroovyBundle.message("lbrack.expected"))){
      marker.done(LIST);
      return LIST;
    }
    if (ParserUtils.getToken(builder, mRBRACK)){
      marker.done(LIST);
      return LIST;
    } else if (ParserUtils.getToken(builder, mCOLON)){
      ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
      marker.done(MAP);
      return MAP;
    } else {
      marker.error(GroovyBundle.message("expression.expected"));
      return LIST;
    }
  }


}