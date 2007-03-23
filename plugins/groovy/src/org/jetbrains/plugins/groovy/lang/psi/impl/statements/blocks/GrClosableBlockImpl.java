package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrClosableBlockImpl extends GroovyPsiElementImpl implements GrClosableBlock {

  public GrClosableBlockImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Closable block";
  }
}