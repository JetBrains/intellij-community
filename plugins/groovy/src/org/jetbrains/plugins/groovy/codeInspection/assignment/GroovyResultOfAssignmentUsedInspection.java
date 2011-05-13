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
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;

import java.util.List;

public class GroovyResultOfAssignmentUsedInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return ASSIGNMENT_ISSUES;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Result of assignment used";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Result of assignment expression used #loc";

  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitAssignmentExpression(GrAssignmentExpression grAssignmentExpression) {
      super.visitAssignmentExpression(grAssignmentExpression);
      final PsiElement parent = grAssignmentExpression.getParent();
      final GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(grAssignmentExpression);
      if (parent instanceof GrCodeBlock || parent instanceof GroovyFile || parent instanceof GrControlStatement || parent == null || parent instanceof GrCaseSection) {
        //check for method that has void return type. so it does not matter what return statements are.
        if (flowOwner instanceof GrOpenBlock) {
          final PsiElement flowParent = flowOwner.getParent();
          if (flowParent instanceof PsiMethod &&
              (((PsiMethod)flowParent).getReturnType() == PsiType.VOID || ((PsiMethod)flowParent).isConstructor())) {
            return;
          }
        }
        final List<GrStatement> returns = ControlFlowUtils.collectReturns(flowOwner);
        if (!returns.contains(grAssignmentExpression)) return;
      }
      registerError(grAssignmentExpression);
    }
  }
}