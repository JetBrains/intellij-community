package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArguments;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgument;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrArgumentImpl extends GroovyPsiElementImpl implements GrArgument {

  public GrArgumentImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Composite argument";
  }
}