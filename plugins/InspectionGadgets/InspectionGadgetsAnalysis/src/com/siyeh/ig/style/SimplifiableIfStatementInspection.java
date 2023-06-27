// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.util.ObjectUtils.tryCast;

public class SimplifiableIfStatementInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  public boolean DONT_WARN_ON_TERNARY = true;
  public boolean DONT_WARN_ON_CHAINED_ID = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("DONT_WARN_ON_TERNARY", InspectionGadgetsBundle.message(
        "inspection.simplifiable.if.statement.option.dont.warn.on.ternary")),
      checkbox("DONT_WARN_ON_CHAINED_ID", InspectionGadgetsBundle.message("trivial.if.option.ignore.chained")));
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitIfStatement(@NotNull PsiIfStatement ifStatement) {
        IfConditionalModel model = IfConditionalModel.from(ifStatement, false);
        if (model == null) return;
        ConditionalExpressionGenerator generator = ConditionalExpressionGenerator.from(model);
        if (generator == null) return;
        String operator = generator.getTokenType();
        if (operator.isEmpty()) return;
        boolean infoLevel = operator.equals("?:") && (DONT_WARN_ON_TERNARY ||
                                                      model.getThenExpression() instanceof PsiConditionalExpression ||
                                                      model.getElseExpression() instanceof PsiConditionalExpression) ||
                            DONT_WARN_ON_CHAINED_ID && ControlFlowUtils.isElseIf(ifStatement);
        if (!isOnTheFly && infoLevel) return;
        holder.registerProblem(ifStatement.getFirstChild(),
                               InspectionGadgetsBundle.message("inspection.simplifiable.if.statement.message", operator),
                               infoLevel ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new SimplifiableIfStatementFix(operator));
      }
    };
  }

  public static void tryJoinDeclaration(PsiElement result) {
    if (!(result instanceof PsiExpressionStatement)) return;
    PsiAssignmentExpression assignment = tryCast(((PsiExpressionStatement)result).getExpression(), PsiAssignmentExpression.class);
    if (assignment == null) return;
    if (!assignment.getOperationTokenType().equals(JavaTokenType.EQ)) return;
    PsiReferenceExpression ref = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
    if (ref == null) return;
    PsiDeclarationStatement declaration = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(result), PsiDeclarationStatement.class);
    if (declaration == null) return;
    PsiElement[] elements = declaration.getDeclaredElements();
    if (elements.length != 1) return;
    PsiLocalVariable var = tryCast(elements[0], PsiLocalVariable.class);
    if (var == null || !ref.isReferenceTo(var)) return;
    final PsiExpression rhs = assignment.getRExpression();
    assert rhs != null;
    boolean readBeforeWritten = SyntaxTraverser.psiTraverser(rhs)
      .filter(PsiReferenceExpression.class)
      .filter(r -> r.isReferenceTo(var) && PsiUtil.isAccessedForReading(r))
      .isNotEmpty();
    if (readBeforeWritten) return;
    CommentTracker ct = new CommentTracker();
    var.setInitializer(ct.markUnchanged(rhs));
    ct.deleteAndRestoreComments(result);
  }

  private static class SimplifiableIfStatementFix extends PsiUpdateModCommandQuickFix {
    private final String myOperator;

    SimplifiableIfStatementFix(String operator) {
      myOperator = operator;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
      return InspectionGadgetsBundle.message("inspection.simplifiable.if.statement.fix.name", myOperator);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.simplifiable.if.statement.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
      if (ifStatement == null) return;

      IfConditionalModel model = IfConditionalModel.from(ifStatement, false);
      if (model == null) return;
      ConditionalExpressionGenerator generator = ConditionalExpressionGenerator.from(model);
      if (generator == null) return;
      CommentTracker commentTracker = new CommentTracker();
      String conditional = generator.generate(commentTracker);
      commentTracker.replace(model.getThenExpression(), conditional);
      PsiStatement branch = model.getElseBranch();
      if (!PsiTreeUtil.isAncestor(ifStatement, branch, true) && !(branch instanceof PsiDeclarationStatement)) {
        commentTracker.delete(branch);
      }
      PsiElement result = commentTracker.replaceAndRestoreComments(ifStatement, model.getThenBranch());
      tryJoinDeclaration(result);
    }
  }
}
