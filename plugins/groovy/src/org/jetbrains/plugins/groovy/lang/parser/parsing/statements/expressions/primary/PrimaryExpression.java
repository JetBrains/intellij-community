package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Ilya.Sergey
 */
public class PrimaryExpression implements Construction {

  public static GroovyElementType parse(PsiBuilder builder){

    if (ParserUtils.getToken(builder, mIDENT)) return PRIMARY_EXXPRESSION;
    if (ParserUtils.getToken(builder, TokenSets.CONSTANTS)) return PRIMARY_EXXPRESSION;
    if (ParserUtils.getToken(builder, TokenSets.BUILT_IN_TYPE)) return PRIMARY_EXXPRESSION;
    if (ParserUtils.getToken(builder, kTHIS)) return PRIMARY_EXXPRESSION;
    if (ParserUtils.getToken(builder, kSUPER)) return PRIMARY_EXXPRESSION;

    // TODO realize me!

    // TODO Add Gstring parsing!

    return WRONGWAY;
  }


}