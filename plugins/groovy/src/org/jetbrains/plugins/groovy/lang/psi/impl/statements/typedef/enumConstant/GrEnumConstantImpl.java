package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class GrEnumConstantImpl extends GroovyPsiElementImpl {
  public GrEnumConstantImpl (@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Enumeration constant";
  }
}
