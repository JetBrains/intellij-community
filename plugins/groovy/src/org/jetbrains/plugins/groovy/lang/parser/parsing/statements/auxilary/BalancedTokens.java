package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.auxilary;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class BalancedTokens implements Construction  {
  public static IElementType parse (PsiBuilder builder) {
    //todo: handle differents brackets cases

    IElementType balancedToken;
    do {
      balancedToken = BalancedBrackets.parse(builder);
    } while(!tWRONG_SET.contains(balancedToken));

    return BALANCED_TOKENS;
  }
}
