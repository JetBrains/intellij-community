// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyMultipleReturnPointsPerMethodInspectionBase extends GroovyMethodMetricInspection {

  public GroovyMultipleReturnPointsPerMethodInspectionBase() {
    super(1);
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    final Integer returnPointCount = (Integer) infos[0];
    return GroovyBundle.message("inspection.message.ref.has.0.return.points", returnPointCount);

  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new MultipleReturnPointsVisitor();
  }

  private class MultipleReturnPointsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull GrMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      final int returnPointCount = calculateReturnPointCount(method);
      if (returnPointCount <= getLimit()) {
        return;
      }
      registerMethodError(method, Integer.valueOf(returnPointCount));
    }

    private int calculateReturnPointCount(GrMethod method) {
      final ReturnPointCountVisitor visitor =
          new ReturnPointCountVisitor();
      method.accept(visitor);
      final int count = visitor.getCount();
      if (!mayFallThroughBottom(method)) {
        return count;
      }
      final GrCodeBlock body = method.getBlock();
      if (body == null) {
        return count;
      }
      final GrStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return count + 1;
      }
      final GrStatement lastStatement =
          statements[statements.length - 1];
      if (ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
        return count + 1;
      }
      return count;
    }

    private static boolean mayFallThroughBottom(GrMethod method) {
      if (method.isConstructor()) {
        return true;
      }
      final PsiType returnType = method.getReturnType();
      return PsiTypes.voidType().equals(returnType);
    }
  }
}
