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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.siyeh.InspectionGadgetsBundle.message;

public class UnnecessaryFinalOnLocalVariableOrParameterInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings({"PublicField"})
  public boolean onlyWarnOnAbstractMethods = false;

  @SuppressWarnings("PublicField")
  public boolean reportLocalVariables = true;

  @SuppressWarnings("PublicField")
  public boolean reportParameters = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return message("unnecessary.final.on.local.variable.or.parameter.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiVariable variable = (PsiVariable)infos[0];
    final String variableName = variable.getName();
    if (variable instanceof PsiParameter) {
      return message("unnecessary.final.on.parameter.problem.descriptor", variableName);
    }
    else {
      return message("unnecessary.final.on.local.variable.problem.descriptor", variableName);
    }
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    final JCheckBox box1 = panel.addCheckboxEx(message("unnecessary.final.report.local.variables.option"), "reportLocalVariables");
    final JCheckBox box2 = panel.addCheckboxEx(message("unnecessary.final.report.parameters.option"), "reportParameters");
    panel.addDependentCheckBox(message("unnecessary.final.on.parameter.only.interface.option"), "onlyWarnOnAbstractMethods", box2);

    box1.addChangeListener(e -> {
      if (!box1.isSelected()) box2.setSelected(true);
    });
    box2.addChangeListener(e -> {
      if (!box2.isSelected()) box1.setSelected(true);
    });
    return panel;
  }


  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryFinalOnLocalVariableOrParameterVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new RemoveModifierFix(PsiModifier.FINAL);
  }

  private class UnnecessaryFinalOnLocalVariableOrParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      if (!reportLocalVariables) {
        return;
      }
      final PsiElement[] declaredElements = statement.getDeclaredElements();
      if (declaredElements.length == 0) {
        return;
      }
      final PsiElement firstElement = declaredElements[0];
      if (!(firstElement instanceof PsiLocalVariable)) {
        return;
      }
      final PsiLocalVariable firstVariable = (PsiLocalVariable)firstElement;
      if (!firstVariable.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiCodeBlock containingBlock = PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
      if (containingBlock == null) {
        return;
      }
      for (PsiElement declaredElement : declaredElements) {
        final PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
        if (isNecessaryFinal(variable, containingBlock)) {
          return;
        }
      }
      final PsiLocalVariable variable = (PsiLocalVariable)firstElement;
      registerModifierError(PsiModifier.FINAL, variable, variable);
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!reportParameters) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      for (final PsiParameter parameter : parameters) {
        checkParameter(method, parameter);
      }
    }

    private void checkParameter(PsiMethod method, PsiParameter parameter) {
      if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        registerModifierError(PsiModifier.FINAL, parameter, parameter);
      }
      else if (!onlyWarnOnAbstractMethods) {
        check(parameter);
      }
    }

    @Override
    public void visitTryStatement(PsiTryStatement statement) {
      super.visitTryStatement(statement);
      if (onlyWarnOnAbstractMethods || !reportParameters) {
        return;
      }
      final PsiResourceList resourceList = statement.getResourceList();
      if (resourceList != null) {
        for (PsiResourceListElement element : resourceList) {
          if (element instanceof PsiResourceVariable) {
            final PsiResourceVariable variable = (PsiResourceVariable)element;
            if (variable.hasModifierProperty(PsiModifier.FINAL)) {
              registerModifierError(PsiModifier.FINAL, variable, variable);
            }
          }
        }
      }
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      for (PsiCatchSection catchSection : catchSections) {
        final PsiParameter parameter = catchSection.getParameter();
        final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
        if (parameter == null || catchBlock == null) {
          continue;
        }
        if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
          continue;
        }
        if (parameter.getType() instanceof PsiDisjunctionType || !isNecessaryFinal(parameter, parameter.getDeclarationScope())) {
          registerModifierError(PsiModifier.FINAL, parameter, parameter);
        }
      }
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      if (onlyWarnOnAbstractMethods || !reportParameters) {
        return;
      }
      final PsiParameter parameter = statement.getIterationParameter();
      if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      check(parameter);
    }

    private boolean isNecessaryFinal(PsiVariable parameter, PsiElement context) {
      return !PsiUtil.isLanguageLevel8OrHigher(parameter) && VariableAccessUtils.variableIsUsedInInnerClass(parameter, context);
    }

    private void check(PsiParameter parameter) {
      if (!isNecessaryFinal(parameter, parameter.getDeclarationScope())) {
        registerModifierError(PsiModifier.FINAL, parameter, parameter);
      }
    }
  }
}