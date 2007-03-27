package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

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
    if (TokenSets.MODIFIERS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      return MODIFIERS;
    }
    return WRONGWAY;
  }
}
