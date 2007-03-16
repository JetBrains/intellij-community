package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.regex;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.regex.GrRegexExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrRegexExprImpl extends GroovyPsiElementImpl implements GrRegexExpression {

  public GrRegexExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Regex expression";
  }

}