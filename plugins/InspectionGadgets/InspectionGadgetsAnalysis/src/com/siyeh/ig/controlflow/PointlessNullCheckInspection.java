/*
 * Copyright 2011-2016 Jetbrains s.r.o.
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SetInspectionOptionFix;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * This inspection finds instances of null checks followed by an instanceof check
 * on the same variable. For instance:
 * <pre>{@code
 * if (x != null && x instanceof String) { ... }
 * }</pre>
 * The instanceof operator returns false when passed a null, so the null check is pointless.
 *
 * @author Lars Fischer
 * @author Etienne Studer
 * @author Hamlet D'Arcy
 */
public class PointlessNullCheckInspection extends BaseInspection {
  private static final String REPORT_CALLS_OPTION = "REPORT_CALLS";

  public boolean REPORT_CALLS = true;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("pointless.nullcheck.display.name");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("pointless.nullcheck.option.report.calls"), this, "REPORT_CALLS");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    PsiExpression parent = PsiTreeUtil.getParentOfType((PsiElement)infos[1], PsiInstanceOfExpression.class, PsiMethodCallExpression.class);
    if (parent instanceof PsiMethodCallExpression) {
      return InspectionGadgetsBundle.message("pointless.nullcheck.problem.descriptor.call",
                                             ((PsiMethodCallExpression)parent).getMethodExpression().getReferenceName());
    }
    return InspectionGadgetsBundle.message("pointless.nullcheck.problem.descriptor.instanceof");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessNullCheckVisitor(REPORT_CALLS);
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    PsiExpression parent = PsiTreeUtil.getParentOfType((PsiElement)infos[1], PsiInstanceOfExpression.class, PsiMethodCallExpression.class);
    PointlessNullCheckFix removeNullCheckFix = new PointlessNullCheckFix(expression.getText());
    if (parent instanceof PsiMethodCallExpression) {
      SetInspectionOptionFix disableOnCallsFix = new SetInspectionOptionFix(this, REPORT_CALLS_OPTION, InspectionGadgetsBundle
        .message("pointless.nullcheck.option.report.calls.off"), false);
      return new InspectionGadgetsFix[]{removeNullCheckFix, new DelegatingFix(disableOnCallsFix)};
    }
    else {
      return new InspectionGadgetsFix[]{removeNullCheckFix};
    }
  }

  private static class PointlessNullCheckFix extends InspectionGadgetsFix {

    private final String myExpressionText;

    public PointlessNullCheckFix(String expressionText) {
      myExpressionText = expressionText;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("pointless.nullcheck.simplify.quickfix", myExpressionText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify";
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiPolyadicExpression polyadicExpression = PsiTreeUtil.getParentOfType(element, PsiPolyadicExpression.class);
      if (polyadicExpression == null) return;
      PsiElement[] children = polyadicExpression.getChildren();

      // We know that at least one operand is present after current, so we just remove everything till the next operand
      int start = IntStreamEx.ofIndices(children, child -> PsiTreeUtil.isAncestor(child, element, false)).findFirst().orElse(-1);
      if (start == -1) return;
      int end = IntStreamEx.range(start + 1, children.length).findFirst(idx -> children[idx] instanceof PsiExpression).orElse(-1);
      if (end == -1) return;
      CommentTracker ct = new CommentTracker();
      String replacement = IntStreamEx.range(0, start).append(IntStreamEx.range(end, children.length)).elements(children)
        .map(ct::text).joining();
      ct.replaceAndRestoreComments(polyadicExpression, replacement);
    }
  }

  private static class PointlessNullCheckVisitor extends BaseInspectionVisitor {
    private final boolean myReportCalls;

    private PointlessNullCheckVisitor(boolean reportCalls) {
      myReportCalls = reportCalls;
    }

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType operationTokenType = expression.getOperationTokenType();
      if (operationTokenType.equals(JavaTokenType.ANDAND)) {
        checkAndChain(expression);
      }
      else if (operationTokenType.equals(JavaTokenType.OROR)) {
        checkOrChain(expression);
      }
    }

    private void checkOrChain(PsiPolyadicExpression expression) {
      final PsiExpression[] operands = expression.getOperands();
      for (int i = 0; i < operands.length - 1; i++) {
        PsiBinaryExpression binaryExpression = tryCast(PsiUtil.skipParenthesizedExprDown(operands[i]), PsiBinaryExpression.class);
        if (binaryExpression == null) continue;
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.EQEQ)) continue;

        for (int j = i + 1; j < operands.length; j++) {
          final PsiExpression implicitCheckCandidate = BoolUtils.getNegated(PsiUtil.skipParenthesizedExprDown(operands[j]));
          if (checkExpressions(operands, i, j, binaryExpression, implicitCheckCandidate)) {
            return;
          }
        }
      }
    }

    private void checkAndChain(PsiPolyadicExpression expression) {
      final PsiExpression[] operands = expression.getOperands();
      for (int i = 0; i < operands.length - 1; i++) {
        PsiBinaryExpression binaryExpression = tryCast(PsiUtil.skipParenthesizedExprDown(operands[i]), PsiBinaryExpression.class);
        if (binaryExpression == null) continue;
        IElementType tokenType = binaryExpression.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.NE)) continue;

        for (int j = i + 1; j < operands.length; j++) {
          PsiExpression implicitCheckCandidate = PsiUtil.skipParenthesizedExprDown(operands[j]);
          if (checkExpressions(operands, i, j, binaryExpression, implicitCheckCandidate)) {
            return;
          }
        }
      }
    }

    private boolean checkExpressions(PsiExpression[] operands,
                                     int i,
                                     int j,
                                     PsiBinaryExpression binaryExpression,
                                     PsiExpression implicitCheckCandidate) {
      final PsiReferenceExpression explicitCheckReference = getReferenceFromNullCheck(binaryExpression);
      if (explicitCheckReference == null) return false;
      final PsiVariable variable = tryCast(explicitCheckReference.resolve(), PsiVariable.class);
      final PsiReferenceExpression implicitCheckReference = getReferenceFromImplicitNullCheckExpression(implicitCheckCandidate);
      if (implicitCheckReference == null || !implicitCheckReference.isReferenceTo(variable)) return false;
      if (isVariableUsed(operands, i, j, variable)) return false;
      registerError(binaryExpression, binaryExpression, implicitCheckReference);
      return true;
    }

    private static boolean isVariableUsed(PsiExpression[] operands, int i, int j, PsiVariable variable) {
      return Arrays.stream(operands, i + 1, j).anyMatch(op -> VariableAccessUtils.variableIsUsed(variable, op));
    }

    @Nullable
    private static PsiReferenceExpression getReferenceFromNullCheck(PsiBinaryExpression expression) {
      PsiExpression comparedWithNull = ExpressionUtils.getValueComparedWithNull(expression);
      return tryCast(PsiUtil.skipParenthesizedExprDown(comparedWithNull), PsiReferenceExpression.class);
    }

    @Nullable
    private PsiReferenceExpression getReferenceFromImplicitNullCheckExpression(PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      PsiReferenceExpression checked = getReferenceFromInstanceofExpression(expression);
      if (checked == null && myReportCalls) {
        checked = getReferenceFromBooleanCall(expression);
      }
      if (checked == null) {
        checked = getReferenceFromOrChain(expression);
      }
      return checked;
    }

    @Nullable
    private static PsiReferenceExpression getReferenceFromInstanceofExpression(PsiExpression expression) {
      if (!(expression instanceof PsiInstanceOfExpression)) return null;
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(((PsiInstanceOfExpression)expression).getOperand());
      return tryCast(operand, PsiReferenceExpression.class);
    }

    @Nullable
    private static PsiReferenceExpression getReferenceFromBooleanCall(PsiExpression expression) {
      if (!(expression instanceof PsiMethodCallExpression)) return null;
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (!PsiType.BOOLEAN.equals(call.getType())) return null;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier != null && SideEffectChecker.mayHaveSideEffects(qualifier)) return null;
      PsiMethod method = call.resolveMethod();
      if (method == null) return null;
      List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, call);
      if (contracts.isEmpty()) return null;
      MethodContract contract = tryCast(contracts.get(0), StandardMethodContract.class);
      if (contract == null || !contract.getReturnValue().equals(ContractReturnValue.returnFalse())) return null;
      ContractValue condition = ContainerUtil.getOnlyItem(contract.getConditions());
      if (condition == null) return null;
      int idx = condition.getNullCheckedArgument(true).orElse(-1);
      if (idx == -1) return null;
      PsiExpression[] args = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions();
      if (args.length <= idx || method.isVarArgs() && idx == args.length - 1) return null;
      PsiReferenceExpression reference = tryCast(args[idx], PsiReferenceExpression.class);
      if (reference == null) return null;
      PsiVariable target = tryCast(reference.resolve(), PsiVariable.class);
      if (target == null) return null;
      if (!SyntaxTraverser.psiTraverser(call).filter(PsiReference.class)
        .filter(ref -> !reference.equals(ref) && ref.isReferenceTo(target)).isEmpty()) {
        // variable is reused for something else
        return null;
      }
      if (Stream.of(args).anyMatch(SideEffectChecker::mayHaveSideEffects)) return null;
      return reference;
    }

    @Nullable
    private PsiReferenceExpression getReferenceFromOrChain(PsiExpression expression) {
      if (!(expression instanceof PsiPolyadicExpression)) return null;
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.OROR != tokenType) return null;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final PsiReferenceExpression referenceExpression = getReferenceFromImplicitNullCheckExpression(operands[0]);
      if (referenceExpression == null) return null;
      final PsiVariable variable = tryCast(referenceExpression.resolve(), PsiVariable.class);
      for (int i = 1, operandsLength = operands.length; i < operandsLength; i++) {
        final PsiReferenceExpression reference2 = getReferenceFromImplicitNullCheckExpression(operands[i]);
        if (reference2 == null || !reference2.isReferenceTo(variable)) {
          return null;
        }
      }
      return referenceExpression;
    }
  }
}
