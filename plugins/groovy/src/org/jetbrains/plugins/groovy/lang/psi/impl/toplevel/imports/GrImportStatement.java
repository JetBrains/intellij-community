package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrImportStatement extends GroovyPsiElementImpl {

  public GrImportStatement(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Import expressions";
  }
}
