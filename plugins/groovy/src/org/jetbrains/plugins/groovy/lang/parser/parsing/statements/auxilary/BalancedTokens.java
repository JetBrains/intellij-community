package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.auxilary;

import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class BalancedTokens implements GroovyElementTypes {
  public static IElementType parse (PsiBuilder builder) {
    //todo: handle differents brackets cases

    IElementType balancedToken;
    do {
      balancedToken = BalancedBrackets.parse(builder);
    } while(!WRONGWAY.equals(balancedToken));

    return BALANCED_TOKENS;
  }
}
