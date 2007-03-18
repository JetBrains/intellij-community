package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public class StrictContextExpression implements GroovyElementTypes {
    public static IElementType parse(PsiBuilder builder) {
      return WRONGWAY;          
    }
}
