package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrPackageDefinitionImpl extends GroovyPsiElementImpl implements GrPackageDefinition {

  public GrPackageDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Package definition";
  }
}
