package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArguments;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrArgumentsImpl extends GroovyPsiElementImpl implements GrArguments {

  public GrArgumentsImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Arguments";
  }
}