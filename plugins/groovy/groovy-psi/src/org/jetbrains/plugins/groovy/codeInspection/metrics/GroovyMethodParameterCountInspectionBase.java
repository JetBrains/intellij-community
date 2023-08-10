// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.LibraryUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyMethodParameterCountInspectionBase extends GroovyMethodMetricInspection {

  public GroovyMethodParameterCountInspectionBase() {
    super(5);
  }

  @Override
  public String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.method.ref.contains.too.many.parameters.0.1", args[0], args[1]);
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
      final GrParameter[] parameters = grMethod.getParameters();
      final int limit = getLimit();
      if (parameters.length <= limit) {
        return;
      }
      if (LibraryUtil.isOverrideOfLibraryMethod(grMethod)) {
        return;
      }
      registerMethodError(grMethod, parameters.length, limit);
    }
  }
}
