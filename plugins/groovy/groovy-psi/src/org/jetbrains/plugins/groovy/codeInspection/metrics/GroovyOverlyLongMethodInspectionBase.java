// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyOverlyLongMethodInspectionBase extends GroovyMethodMetricInspection {

  public GroovyOverlyLongMethodInspectionBase() {
    super(30);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return "Overly long method";
  }

  @Override
  public String buildErrorString(Object... args) {
    return "Method '#ref' is too long ( statement count =" + args[0] + '>' + args[1] + ')';
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(@NotNull GrMethod method) {
      super.visitMethod(method);
      final int limit = getLimit();
      final StatementCountVisitor visitor = new StatementCountVisitor();
      final GrOpenBlock block = method.getBlock();
      if (block == null) return;
      block.accept(visitor);
      final int statementCount = visitor.getStatementCount();
      if (statementCount <= limit) {
        return;
      }
      registerMethodError(method, statementCount, limit);
    }
  }
}
