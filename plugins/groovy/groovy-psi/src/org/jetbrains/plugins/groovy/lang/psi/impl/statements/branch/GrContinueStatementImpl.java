// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

public class GrContinueStatementImpl extends GrFlowInterruptingStatementImpl implements GrContinueStatement {
  public GrContinueStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitContinueStatement(this);
  }

  @Override
  public String toString() {
    return "CONTINUE statement";
  }

  @Override
  public @Nullable GrStatement findTargetStatement() {
    return  ResolveUtil.resolveLabelTargetStatement(getLabelName(), this, false);
  }

  @Override
  public GrLabeledStatement resolveLabel() {
    return  ResolveUtil.resolveLabeledStatement(getLabelName(), this, false);
  }

  @Override
  public String getStatementText() {
    return "continue";
  }
}
