package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.04.2007
 */
public class GrTypeElementImpl extends GroovyPsiElementImpl implements GrTypeElement {
  public GrTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Type element";
  }
}
