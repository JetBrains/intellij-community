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

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import one.util.streamex.Joining;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;

public class EnumSwitchStatementWhichMissesCasesInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreSwitchStatementsWithDefault = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String enumName = (String)infos[0];
    @SuppressWarnings("unchecked") Set<String> names = (Set<String>)infos[1];
    if (names.size() == 1) {
      return InspectionGadgetsBundle
        .message("enum.switch.statement.which.misses.cases.problem.descriptor.single", enumName, names.iterator().next());
    }
    String namesString = StreamEx.of(names).map(name -> "'" + name + "'").mapLast("and "::concat)
      .collect(Joining.with(", ").maxChars(50).cutAfterDelimiter());
    return InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.problem.descriptor", enumName, namesString);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.option"),
                                          this, "ignoreSwitchStatementsWithDefault");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EnumSwitchStatementWhichMissesCasesVisitor();
  }

  private class EnumSwitchStatementWhichMissesCasesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      final PsiExpression expression = statement.getExpression();
      PsiCodeBlock body = statement.getBody();
      if (expression == null || body == null) return;
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
      if (aClass == null || !aClass.isEnum()) return;
      Set<String> constants = StreamEx.of(aClass.getAllFields()).select(PsiEnumConstant.class).map(PsiEnumConstant::getName)
        .toCollection(LinkedHashSet::new);
      if (constants.isEmpty()) return;
      for (final PsiSwitchLabelStatement child : PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatement.class)) {
        if (child.isDefaultCase() && ignoreSwitchStatementsWithDefault) return;
        PsiExpression caseValue = child.getCaseValue();
        if (!(caseValue instanceof PsiReferenceExpression)) {
          // Probably syntax error
          return;
        }
        PsiEnumConstant enumConstant = ObjectUtils.tryCast(((PsiReferenceExpression)caseValue).resolve(), PsiEnumConstant.class);
        if (enumConstant == null || enumConstant.getContainingClass() != aClass) {
          // Unresolved constant: do not report anything on incomplete code
          return;
        }
        constants.remove(enumConstant.getName());
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
      registerStatementError(statement, aClass.getQualifiedName(), constants);
    }
  }
}
