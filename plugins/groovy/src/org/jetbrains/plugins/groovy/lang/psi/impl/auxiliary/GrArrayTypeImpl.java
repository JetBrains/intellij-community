package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrTypeCast;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrArrayType;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrArrayTypeImpl extends GroovyPsiElementImpl implements GrArrayType {

  public GrArrayTypeImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Array type";
  }
}