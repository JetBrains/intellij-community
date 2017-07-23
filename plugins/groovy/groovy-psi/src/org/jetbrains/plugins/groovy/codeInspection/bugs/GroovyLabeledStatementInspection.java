/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
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
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("label.already.used", args);
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return GroovyInspectionBundle.message("check.labeled.statement");
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
