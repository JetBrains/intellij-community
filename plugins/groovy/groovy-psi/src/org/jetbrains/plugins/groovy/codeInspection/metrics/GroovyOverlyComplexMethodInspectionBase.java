// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyOverlyComplexMethodInspectionBase extends GroovyMethodMetricInspection {

  public GroovyOverlyComplexMethodInspectionBase() {
    super(10);
  }

  @Override
  public String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.method.ref.overly.complex.cyclomatic.complexity", args[0], args[1]);
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(@NotNull GrMethod grMethod) {
      super.visitMethod(grMethod);
      final int limit = getLimit();
      final CyclomaticComplexityVisitor visitor = new CyclomaticComplexityVisitor();
      final GrOpenBlock body = grMethod.getBlock();
      if (body == null) {
        return;
      }
      body.accept(visitor);
      final int complexity = visitor.getComplexity();
      if (complexity <= limit) {
        return;
      }
      registerMethodError(grMethod, complexity, limit);
    }
  }
}
