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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class UnusedLabelInspection extends StatementInspection {

  private final UnusedLabelFix fix = new UnusedLabelFix();

  public String getGroupDisplayName() {
    return GroupNames.CONTROL_FLOW_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnusedLabelVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class UnusedLabelFix extends InspectionGadgetsFix {
    public String getName() {
      return "Remove unused label";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement label = descriptor.getPsiElement();
      final PsiLabeledStatement statement =
        (PsiLabeledStatement)label.getParent();
      assert statement != null;
      final PsiStatement labeledStatement = statement.getStatement();
      final String statementText = labeledStatement.getText();
      replaceStatement(statement, statementText);
    }
  }

  private static class UnusedLabelVisitor extends StatementInspectionVisitor {

    public void visitLabeledStatement(PsiLabeledStatement statement) {
      if (containsBreakOrContinueForLabel(statement)) {
        return;
      }
      final PsiIdentifier labelIdentifier =
        statement.getLabelIdentifier();
      registerError(labelIdentifier);
    }

    private static boolean containsBreakOrContinueForLabel(PsiLabeledStatement statement) {
      final LabelFinder labelFinder = new LabelFinder(statement);
      statement.accept(labelFinder);
      return labelFinder.jumpFound();
    }
  }

  private static class LabelFinder extends PsiRecursiveElementVisitor {
    private boolean found = false;
    private String label = null;

    private LabelFinder(PsiLabeledStatement target) {
      super();
      final PsiIdentifier labelIdentifier = target.getLabelIdentifier();
      label = labelIdentifier.getText();
    }

    public void visitElement(@NotNull PsiElement element) {
      if (!found) {
        super.visitElement(element);
      }
    }

    public void visitContinueStatement(@NotNull PsiContinueStatement continueStatement) {
      if (found) {
        return;
      }
      super.visitContinueStatement(continueStatement);

      final PsiIdentifier labelIdentifier =
        continueStatement.getLabelIdentifier();
      if (labelMatches(labelIdentifier)) {
        found = true;
      }
    }

    public void visitBreakStatement(@NotNull PsiBreakStatement breakStatement) {
      if (found) {
        return;
      }
      super.visitBreakStatement(breakStatement);

      final PsiIdentifier labelIdentifier =
        breakStatement.getLabelIdentifier();

      if (labelMatches(labelIdentifier)) {
        found = true;
      }
    }

    private boolean labelMatches(PsiIdentifier labelIdentifier) {
      if (labelIdentifier == null) {
        return false;
      }
      final String labelText = labelIdentifier.getText();
      return labelText.equals(label);
    }

    private boolean jumpFound() {
      return found;
    }
  }
}
