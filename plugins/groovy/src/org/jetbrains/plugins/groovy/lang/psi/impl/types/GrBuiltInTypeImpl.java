package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrArrayTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrBuiltInTypeElement;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrBuiltInTypeImpl extends GroovyPsiElementImpl implements GrBuiltInTypeElement {

  public GrBuiltInTypeImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Built in type";
  }
}