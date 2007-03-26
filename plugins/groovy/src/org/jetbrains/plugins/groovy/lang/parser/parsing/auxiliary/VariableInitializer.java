package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class VariableInitializer implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    return WRONGWAY;
  }
}
