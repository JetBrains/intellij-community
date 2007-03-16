package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportQualId;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrImportQualIdImpl extends GroovyPsiElementImpl implements GrImportQualId {

  public GrImportQualIdImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Import prefix";
  }
}
