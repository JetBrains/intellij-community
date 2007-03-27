package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrArgumentLabelImpl extends GroovyPsiElementImpl implements GrArgumentLabel {

  public GrArgumentLabelImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Argument label";
  }
}