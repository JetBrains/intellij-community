package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public class ForStatement implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker forStmtMarker = builder.mark();

    if (!ParserUtils.getToken(builder, kFOR)) {
      forStmtMarker.rollbackTo();
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mLPAREN)) {
      forStmtMarker.rollbackTo();
      return WRONGWAY;
    }

    //todo
    return WRONGWAY;
  }
}
