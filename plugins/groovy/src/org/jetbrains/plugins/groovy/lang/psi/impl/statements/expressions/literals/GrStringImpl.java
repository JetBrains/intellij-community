package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrIdentifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrStringImpl extends GroovyPsiElementImpl implements GrString {

  public GrStringImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Compound Gstring";
  }
}