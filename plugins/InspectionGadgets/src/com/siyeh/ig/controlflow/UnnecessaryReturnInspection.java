/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryReturnInspection extends StatementInspection {

  private final UnnecessaryReturnFix fix = new UnnecessaryReturnFix();

  public String getID() {
    return "UnnecessaryReturnStatement";
  }

  public String getGroupDisplayName() {
    return GroupNames.CONTROL_FLOW_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public String buildErrorString(PsiElement location) {
    final PsiMethod method =
      PsiTreeUtil.getParentOfType(location, PsiMethod.class);
    assert method != null;
    if (method.isConstructor()) {
      return InspectionGadgetsBundle.message("unnecessary.return.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("unnecessary.return.problem.descriptor1");
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryReturnVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class UnnecessaryReturnFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("unnecessary.return.remove.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement returnKeywordElement = descriptor.getPsiElement();
      final PsiElement returnStatement = returnKeywordElement.getParent();
      assert returnStatement != null;
      deleteElement(returnStatement);
    }
  }

  private static class UnnecessaryReturnVisitor extends StatementInspectionVisitor {

    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);

      if (statement.getContainingFile() instanceof JspFile) {
        return;
      }
      final PsiMethod method =
        PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
      if (method == null) {
        return;
      }
      if (!method.isConstructor()) {
        final PsiType returnType = method.getReturnType();
        if (!PsiType.VOID.equals(returnType)) {
          return;
        }
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      if (ControlFlowUtils.blockCompletesWithStatement(body, statement)) {
        registerStatementError(statement);
      }
    }
  }
}
