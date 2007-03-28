package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrRegexImpl extends GroovyPsiElementImpl implements GrRegex {

  public GrRegexImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Compound regular expression";
  }
}

