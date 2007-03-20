package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrList;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrListImpl extends GroovyPsiElementImpl implements GrList {

  public GrListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "List";
  }
}
