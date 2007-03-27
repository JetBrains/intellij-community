package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrListOrMapImpl extends GroovyPsiElementImpl implements GrListOrMap {

  public GrListOrMapImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Generalized list";
  }
}
