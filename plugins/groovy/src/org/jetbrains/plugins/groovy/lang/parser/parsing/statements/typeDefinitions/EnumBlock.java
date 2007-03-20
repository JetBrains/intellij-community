package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class EnumBlock implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    //see also InterfaceBlock, EnumBlock, AnnotationBlock
    return WRONGWAY;
  }
}
