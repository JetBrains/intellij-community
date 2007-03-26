package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifiers;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public class GrModifiersImpl extends GroovyPsiElementImpl implements GrModifiers {
  public GrModifiersImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Modifiers";
  }
}
