package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class Annotation implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    return WRONGWAY;
  }
}
