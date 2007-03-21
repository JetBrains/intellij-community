package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrArrayTypeElement;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrArrayTypeElementImpl extends GroovyPsiElementImpl implements GrArrayTypeElement {

  public GrArrayTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Array type";
  }
}