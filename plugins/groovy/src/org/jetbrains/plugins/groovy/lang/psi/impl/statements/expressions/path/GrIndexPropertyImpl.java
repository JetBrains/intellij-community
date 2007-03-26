package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrIndexPropertyImpl extends GroovyPsiElementImpl implements GrIndexProperty {

  public GrIndexPropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Property by index";
  }
}