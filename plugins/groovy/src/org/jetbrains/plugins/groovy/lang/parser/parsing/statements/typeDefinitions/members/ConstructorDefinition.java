package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 23.03.2007
 */
public class ConstructorDefinition implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    return WRONGWAY;
  }
}
