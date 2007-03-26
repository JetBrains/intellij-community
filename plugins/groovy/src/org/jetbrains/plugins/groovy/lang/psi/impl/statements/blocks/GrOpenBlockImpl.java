package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GrOpenBlockImpl extends GroovyPsiElementImpl implements GrOpenBlock {

  public GrOpenBlockImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString(){
    return "Open block";
  }
}