/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrBlockStatementImpl extends GroovyPsiElementImpl implements GrBlockStatement {

  public GrBlockStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitBlockStatement(this);
  }

  public String toString() {
    return "Block statement";
  }

  public GrOpenBlock getBlock() {
    return findChildByClass(GrOpenBlock.class);
  }
}
