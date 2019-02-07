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
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static com.intellij.codeInspection.ProblemHighlightType.INFORMATION;

public class UnnecessaryDefaultInspection extends BaseInspection {

  public boolean onlyReportSwitchExpressions = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.default.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.default.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("unnecessary.default.expressions.option"),
                                          this, "onlyReportSwitchExpressions");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new DeleteDefaultFix();
  }

  private static class DeleteDefaultFix extends InspectionGadgetsFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.default.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (element instanceof PsiSwitchLabelStatementBase) {
        DeleteSwitchLabelFix.deleteLabel((PsiSwitchLabelStatementBase)element);
      }
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return !onlyReportSwitchExpressions || HighlightUtil.Feature.ENHANCED_SWITCH.isAvailable(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryDefaultVisitor();
  }

  private class UnnecessaryDefaultVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchExpression(PsiSwitchExpression expression) {
      super.visitSwitchExpression(expression);
      checkSwitchBlock(expression);
    }

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      checkSwitchBlock(statement);
    }

    private void checkSwitchBlock(@NotNull PsiSwitchBlock switchBlock) {
      final PsiSwitchLabelStatementBase defaultStatement = retrieveUnnecessaryDefault(switchBlock);
      if (defaultStatement == null) {
        return;
      }
      final boolean ruleBasedSwitch = defaultStatement instanceof PsiSwitchLabeledRuleStatement;
      final boolean statementSwitch = switchBlock instanceof PsiStatement;
      PsiStatement nextStatement = ruleBasedSwitch
                                   ? ((PsiSwitchLabeledRuleStatement)defaultStatement).getBody()
                                   : PsiTreeUtil.getNextSiblingOfType(defaultStatement, PsiStatement.class);
      if (statementSwitch && nextStatement instanceof PsiThrowStatement) {
        // consider a single throw statement a guard against future changes that update the code only partially
        return;
      }
      while (nextStatement != null) {
        if (isDefaultNeededForInitializationOfVariable(switchBlock)) {
          return;
        }
        if (statementSwitch && !ControlFlowUtils.statementMayCompleteNormally(nextStatement)) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(switchBlock, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
          if (method != null && !PsiType.VOID.equals(method.getReturnType()) &&
              !ControlFlowUtils.statementContainsNakedBreak(nextStatement)) {
            final PsiCodeBlock body = method.getBody();
            assert body != null;
            if (ControlFlowUtils.blockCompletesWithStatement(body, (PsiStatement)switchBlock)) {
              return;
            }
          }
          else {
            break;
          }
        }
        if (ruleBasedSwitch) {
          break;
        }
        nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      }
      final ProblemHighlightType highlightType;
      if (onlyReportSwitchExpressions && statementSwitch) {
        if (!isOnTheFly()) {
          return;
        }
        highlightType = INFORMATION;
      }
      else {
        highlightType = GENERIC_ERROR_OR_WARNING;
      }
      registerError(defaultStatement.getFirstChild(), highlightType);
    }
  }

  private static boolean isDefaultNeededForInitializationOfVariable(PsiSwitchBlock switchBlock) {
    final SmartList<PsiReferenceExpression> expressions = new SmartList<>();
    final PsiElementProcessor.CollectFilteredElements<PsiReferenceExpression> collector =
      new PsiElementProcessor.CollectFilteredElements<>(e -> e instanceof PsiReferenceExpression, expressions);
    PsiTreeUtil.processElements(switchBlock, collector);
    final Set<PsiElement> checked = new THashSet<>();
    for (PsiReferenceExpression expression : expressions) {
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
      if (!(parent instanceof PsiAssignmentExpression)) {
        continue;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      if (JavaTokenType.EQ != assignmentExpression.getOperationTokenType()) {
        continue;
      }
      if (!PsiTreeUtil.isAncestor(assignmentExpression.getLExpression(), expression, false)) {
        continue;
      }
      final PsiElement target = expression.resolve();
      if (!checked.add(target)) {
        continue;
      }
      if (target instanceof PsiLocalVariable || target instanceof PsiField && ((PsiField)target).hasModifierProperty(PsiModifier.FINAL)) {
        final PsiVariable variable = (PsiVariable)target;
        if (variable.getInitializer() != null) {
          continue;
        }
        final PsiElement context = getContext(switchBlock);
        if (context == null) {
          return true;
        }
        try {
          final LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
          final ControlFlow controlFlow = ControlFlowFactory.getInstance(context.getProject()).getControlFlow(context, policy);
          final int switchStart = controlFlow.getStartOffset(switchBlock);
          final int switchEnd = controlFlow.getEndOffset(switchBlock);
          final ControlFlow beforeFlow = new ControlFlowSubRange(controlFlow, 0, switchStart);
          final ControlFlow switchFlow = new ControlFlowSubRange(controlFlow, switchStart, switchEnd);
          if (!ControlFlowUtil.isVariableDefinitelyAssigned(variable, beforeFlow) &&
              ControlFlowUtil.isVariableDefinitelyAssigned(variable, switchFlow) &&
              ControlFlowUtil.needVariableValueAt(variable, controlFlow, switchEnd)) {
            return true;
          }
        }
        catch (AnalysisCanceledException e) {
          return true;
        }
      }
    }
    return false;
  }

  private static PsiElement getContext(PsiElement element) {
    final PsiElement context = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiLambdaExpression.class);
    if (context instanceof PsiField) {
      return ((PsiField)context).getInitializer();
    }
    else if (context instanceof PsiClassInitializer) {
      return ((PsiClassInitializer)context).getBody();
    }
    else if (context instanceof PsiMethod) {
      return ((PsiMethod)context).getBody();
    }
    else if (context instanceof PsiLambdaExpression) {
      return ((PsiLambdaExpression)context).getBody();
    }
    return null;
  }

  @Nullable
  private static PsiSwitchLabelStatementBase retrieveUnnecessaryDefault(PsiSwitchBlock switchBlock) {
    final PsiExpression expression = switchBlock.getExpression();
    if (expression == null) {
      return null;
    }
    final PsiType type = expression.getType();
    if (!(type instanceof PsiClassType)) {
      return null;
    }
    final PsiClassType classType = (PsiClassType)type;
    final PsiClass aClass = classType.resolve();
    if (aClass == null || !aClass.isEnum()) {
      return null;
    }
    final PsiCodeBlock body = switchBlock.getBody();
    if (body == null) {
      return null;
    }
    final Set<PsiEnumConstant> coveredConstants = new THashSet<>();
    PsiSwitchLabelStatementBase result = null;
    for (PsiStatement statement : body.getStatements()) {
      if (!(statement instanceof PsiSwitchLabelStatementBase)) {
        continue;
      }
      final PsiSwitchLabelStatementBase labelStatement = (PsiSwitchLabelStatementBase)statement;
      if (labelStatement.isDefaultCase()) {
        result = labelStatement;
      }
      else {
        final List<PsiEnumConstant> constants = SwitchUtils.findEnumConstants(labelStatement);
        for (PsiEnumConstant constant : constants) {
          if (!coveredConstants.add(constant)) {
            return null; // broken code
          }
        }
      }
    }
    if (result == null) {
      return null;
    }
    for (PsiField field : aClass.getFields()) {
      if (field instanceof PsiEnumConstant && !coveredConstants.remove(field)) {
        return null;
      }
    }
    return !coveredConstants.isEmpty() ? null : result;
  }
}