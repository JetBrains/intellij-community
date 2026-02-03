// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyOverlyNestedMethodInspectionBase extends GroovyMethodMetricInspection {

  public GroovyOverlyNestedMethodInspectionBase() {
    super(5);
  }

  @Override
  public String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.method.ref.overly.nested.nesting.depth", args[0], args[1]);
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(@NotNull GrMethod grMethod) {
      super.visitMethod(grMethod);
      final int limit = getLimit();
      final NestingDepthVisitor visitor = new NestingDepthVisitor();
      final GrOpenBlock body = grMethod.getBlock();
      if (body == null) {
        return;
      }
      body.accept(visitor);
      final int nestingDepth = visitor.getMaximumDepth();
      if (nestingDepth <= limit) {
        return;
      }
      registerMethodError(grMethod, nestingDepth, limit);
    }
  }
}
