package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrIdentifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrLiteral;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrLiteralImpl extends GroovyPsiElementImpl implements GrLiteral {

  public GrLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Literal";
  }
}