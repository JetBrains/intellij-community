/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.CreateMissingSwitchBranchesFix;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EnumSwitchStatementWhichMissesCasesInspection extends AbstractBaseJavaLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean ignoreSwitchStatementsWithDefault = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.display.name");
  }

  @NotNull
  String buildErrorString(String enumName, Set<String> names) {
    if (names.size() == 1) {
      return InspectionGadgetsBundle
        .message("enum.switch.statement.which.misses.cases.problem.descriptor.single", enumName, names.iterator().next());
    }
    String namesString = CreateMissingSwitchBranchesFix.formatMissingBranches(names);
    return InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.problem.descriptor", enumName, namesString);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.option"),
                                          this, "ignoreSwitchStatementsWithDefault");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
        processSwitchBlock(statement);
      }

      @Override
      public void visitSwitchExpression(PsiSwitchExpression expression) {
        processSwitchBlock(expression);
      }

      public void processSwitchBlock(@NotNull PsiSwitchBlock switchBlock) {
        final PsiExpression expression = switchBlock.getExpression();
        if (expression == null) return;
        final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
        if (aClass == null || !aClass.isEnum()) return;
        Set<String> constants = StreamEx.of(aClass.getAllFields()).select(PsiEnumConstant.class).map(PsiEnumConstant::getName)
          .toCollection(LinkedHashSet::new);
        if (constants.isEmpty()) return;
        boolean hasDefault = false;
        ProblemHighlightType highlighting = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        for (PsiSwitchLabelStatementBase child : PsiTreeUtil
          .getChildrenOfTypeAsList(switchBlock.getBody(), PsiSwitchLabelStatementBase.class)) {
          if (child.isDefaultCase()) {
            hasDefault = true;
            if (ignoreSwitchStatementsWithDefault) {
              if (!isOnTheFly) return;
              highlighting = ProblemHighlightType.INFORMATION;
            }
            continue;
          }
          List<PsiEnumConstant> enumConstants = SwitchUtils.findEnumConstants(child);
          if (enumConstants.isEmpty()) {
            // Syntax error or unresolved constant: do not report anything on incomplete code
            return;
          }
          for (PsiEnumConstant constant : enumConstants) {
            if (constant.getContainingClass() != aClass) {
              // Syntax error or unresolved constant: do not report anything on incomplete code
              return;
            }
            constants.remove(constant.getName());
          }
        }
        if (!hasDefault && switchBlock instanceof PsiSwitchExpression) {
          // non-exhaustive switch expression: it's a compilation error 
          // and the compilation fix should be suggested instead of normal inspection
          return;
        }
        if (constants.isEmpty()) return;
        CommonDataflow.DataflowResult dataflow = CommonDataflow.getDataflowResult(expression);
        if (dataflow != null) {
          Set<Object> values = dataflow.getValuesNotEqualToExpression(expression);
          for (Object value : values) {
            if (value instanceof PsiEnumConstant) {
              constants.remove(((PsiEnumConstant)value).getName());
            }
          }
        }
        if (constants.isEmpty()) return;
        String message = buildErrorString(aClass.getQualifiedName(), constants);
        CreateMissingSwitchBranchesFix fix = new CreateMissingSwitchBranchesFix(switchBlock, constants);
        if (highlighting == ProblemHighlightType.INFORMATION ||
            InspectionProjectProfileManager.isInformationLevel(getShortName(), switchBlock)) {
          holder.registerProblem(switchBlock, message, highlighting, fix);
        }
        else {
          int length = switchBlock.getFirstChild().getTextLength();
          holder.registerProblem(switchBlock, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new TextRange(0, length), fix);
          if (isOnTheFly) {
            TextRange range = new TextRange(length, switchBlock.getTextLength());
            holder.registerProblem(switchBlock, message, ProblemHighlightType.INFORMATION, range, fix);
          }
        }
      }
    };
  }
}
