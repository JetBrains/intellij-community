/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public class UnusedLabelInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unused.label.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnusedLabelVisitor();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unused.label.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnusedLabelFix();
  }

  private static class UnusedLabelFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "unused.label.remove.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement label = descriptor.getPsiElement();
      final PsiElement parent = label.getParent();
      if (!(parent instanceof PsiLabeledStatement)) {
        return;
      }
      final PsiLabeledStatement labeledStatement = (PsiLabeledStatement)parent;
      final PsiStatement statement = labeledStatement.getStatement();
      if (statement == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      final String statementText = commentTracker.text(statement);
      PsiReplacementUtil.replaceStatement(labeledStatement, statementText, commentTracker);
    }
  }

  private static class UnusedLabelVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLabeledStatement(PsiLabeledStatement statement) {
      if (containsBreakOrContinueForLabel(statement)) {
        return;
      }
      final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
      registerError(labelIdentifier);
    }

    private static boolean containsBreakOrContinueForLabel(PsiLabeledStatement statement) {
      final LabelFinder labelFinder = new LabelFinder(statement);
      statement.accept(labelFinder);
      return labelFinder.jumpFound();
    }
  }

  private static class LabelFinder extends JavaRecursiveElementWalkingVisitor {

    private boolean found;
    private final String label;

    private LabelFinder(PsiLabeledStatement target) {
      final PsiIdentifier labelIdentifier = target.getLabelIdentifier();
      label = labelIdentifier.getText();
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (found) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitContinueStatement(
      @NotNull PsiContinueStatement continueStatement) {
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

    @Override
    public void visitBreakStatement(
      @NotNull PsiBreakStatement breakStatement) {
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

    boolean jumpFound() {
      return found;
    }
  }
}