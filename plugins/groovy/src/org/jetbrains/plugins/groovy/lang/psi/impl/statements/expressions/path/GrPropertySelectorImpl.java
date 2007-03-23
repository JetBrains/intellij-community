package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrPropertySelectior;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrPropertySelectorImpl extends GroovyPsiElementImpl implements GrPropertySelectior {

  public GrPropertySelectorImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Property selector";
  }
}
