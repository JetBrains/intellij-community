package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 28.03.2007
 */
public class GrTypeArgumentImpl extends GroovyPsiElementImpl implements GrTypeParameter {
  public GrTypeArgumentImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Type argument";
  }
}