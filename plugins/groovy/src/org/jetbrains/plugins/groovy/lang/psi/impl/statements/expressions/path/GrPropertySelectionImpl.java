package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrPropertySelection;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrPropertySelectionImpl extends GroovyPsiElementImpl implements GrPropertySelection {

  public GrPropertySelectionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Property selection";
  }
}
