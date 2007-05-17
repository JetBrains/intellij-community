/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrFinallyClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author ilyas
 */
public class GrFinallyClauseImpl extends GroovyPsiElementImpl implements GrFinallyClause {
  public GrFinallyClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Finally clause";
  }

  public GrOpenBlock getBody() {
    return findChildByClass(GrOpenBlock.class);
  }

}