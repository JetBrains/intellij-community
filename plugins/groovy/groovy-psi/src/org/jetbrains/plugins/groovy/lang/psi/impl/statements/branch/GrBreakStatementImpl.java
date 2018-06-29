// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ilyas
 */
public class GrBreakStatementImpl extends GrFlowInterruptingStatementImpl implements GrBreakStatement {
  public GrBreakStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitBreakStatement(this);
  }

  public String toString() {
    return "BREAK statement";
  }

  @Override
  @Nullable
  public GrStatement findTargetStatement() {
    return ResolveUtil.resolveLabelTargetStatement(getLabelName(), this, true);
  }

  @Override
  public GrLabeledStatement resolveLabel() {
    return ResolveUtil.resolveLabeledStatement(getLabelName(), this, true);
  }

  @Override
  public String getStatementText() {
    return "break";
  }
}
