package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrPackageDefinition extends GroovyPsiElementImpl {

  public GrPackageDefinition(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Package definition";
  }
}
