// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author Maxim.Medvedev
 */
public class GroovyLabeledStatementInspection extends BaseInspection {
  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  @Override
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("label.already.used", args);
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    @Override
    public void visitLabeledStatement(@NotNull GrLabeledStatement labeledStatement) {
      super.visitLabeledStatement(labeledStatement);

      final String name = labeledStatement.getName();
      GrLabeledStatement existing = ResolveUtil.resolveLabeledStatement(name, labeledStatement, true);
      if (existing != null && PsiTreeUtil.isAncestor(existing, labeledStatement, true)) {
        registerError(labeledStatement.getLabel(), name);
      }
    }
  }
}
