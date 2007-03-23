package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */

/*
 * Modifier ::= private
 *            | public
 *            | protected
 *            | static
 *            | transient
 *            | final
 *            | abstract
 *            | native
 *            | threadsafe
 *            | synchronized
 *            | volatile
 *            | srtictfp
 */

public class Modifier implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    if (ParserUtils.validateToken(builder, TokenSets.MODIFIERS)) {
//      ParserUtils.eatElement(builder, MODIFIER);
      builder.advanceLexer();
      return MODIFIER;
    } else {
//      builder.error(GroovyBundle.message("modifier.expected"));
      return WRONGWAY;
    }
  }
}
