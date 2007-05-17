/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWithStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author ilyas
 */
public class GrCatchClauseImpl extends GroovyPsiElementImpl implements GrCatchClause {
  public GrCatchClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Catch clause";
  }

  public GrParameter getParameter() {
    return findChildByClass(GrParameter.class);
  }

  public GrOpenBlock getBody() {
    return findChildByClass(GrOpenBlock.class);
  }

}