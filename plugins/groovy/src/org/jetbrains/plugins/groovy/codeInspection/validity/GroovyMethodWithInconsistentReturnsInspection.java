/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.validity;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyMethodWithInconsistentReturnsInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return VALIDITY_ISSUES;
  }

  @NotNull
  public String getDisplayName() {
    return "Method with inconsistent returns";
  }


  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Method '#ref' has inconsistent return points #loc";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitMethod(GrMethod method) {
      super.visitMethod(method);
      if (!methodHasReturnValues(method)) {
        return;
      }
      if (!methodHasValuelessReturns(method)) {
        return;
      }
      registerMethodError(method);
    }
  }

  private static boolean methodHasReturnValues(GrMethod method) {
    final ReturnValuesVisitor visitor = new ReturnValuesVisitor(method);
    method.accept(visitor);
    return visitor.hasReturnValues();
  }

  private static boolean methodHasValuelessReturns(GrMethod method) {
    final PsiElement lastChild = method.getLastChild();
    if (lastChild instanceof GrBlockStatement) {
      if (ControlFlowUtils.statementMayCompleteNormally((GrStatement) lastChild)) {
        return true;
      }
    }
    final ValuelessReturnVisitor visitor = new ValuelessReturnVisitor(method);
    method.accept(visitor);
    return visitor.hasValuelessReturns();
  }

  private static class ReturnValuesVisitor extends GroovyRecursiveElementVisitor {
    private final GrMethod method;
    private boolean hasReturnValues = false;

    ReturnValuesVisitor(GrMethod method) {
      this.method = method;
    }

    public void visitReturnStatement(GrReturnStatement statement) {
      super.visitReturnStatement(statement);
      if (statement.getReturnValue() != null) {
        final GrMethod containingMethod =
            PsiTreeUtil.getParentOfType(statement, GrMethod.class);
        if (method.equals(containingMethod)) {
          hasReturnValues = true;
        }
      }
    }

    public boolean hasReturnValues() {
      return hasReturnValues;
    }
  }

  private static class ValuelessReturnVisitor extends GroovyRecursiveElementVisitor {
    private final GrMethod method;
    private boolean hasValuelessReturns = false;

    ValuelessReturnVisitor(GrMethod method) {
      this.method = method;
    }

    public void visitReturnStatement(GrReturnStatement statement) {
      super.visitReturnStatement(statement);
      if (statement.getReturnValue() == null) {
        final GrMethod containingMethod =
            PsiTreeUtil.getParentOfType(statement, GrMethod.class);
        if (method.equals(containingMethod)) {
          hasValuelessReturns = true;
        }
      }
    }

    public void visitGrMethod(GrMethod method) {
      // do nothing, so that it doesn't drill into nested methods
    }

    public boolean hasValuelessReturns() {
      return hasValuelessReturns;
    }
  }
}