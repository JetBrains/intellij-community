/*
 * Copyright 2003-2020 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.NormalizeDeclarationFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CStyleArrayDeclarationInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public boolean ignoreVariables = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Object info = infos[0];
    if (info instanceof PsiMethod) {
      return InspectionGadgetsBundle.message("cstyle.array.method.declaration.problem.descriptor");
    }
    final int choice;
    if (info instanceof PsiField) choice = 1;
    else if (info instanceof PsiParameter) choice = 2;
    else if (info instanceof PsiRecordComponent)choice = 3;
    else choice = 4;
    return InspectionGadgetsBundle.message("cstyle.array.variable.declaration.problem.descriptor", Integer.valueOf(choice));
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(JavaAnalysisBundle.message("inspection.c.style.array.declarations.option"), this, "ignoreVariables");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new NormalizeDeclarationFix(true);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CStyleArrayDeclarationVisitor();
  }

  private class CStyleArrayDeclarationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      if (ignoreVariables) {
        return;
      }
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null || typeElement.isInferredType()) {
        return; // Could be true for enum constants or lambda parameters
      }
      final PsiType declaredType = variable.getType();
      if (declaredType.getArrayDimensions() == 0) {
        return;
      }
      final PsiType elementType = typeElement.getType();
      if (elementType.equals(declaredType)) {
        return;
      }
      if (isVisibleHighlight(variable)) {
        registerVariableError(variable, variable);
      }
      else {
        registerError(variable, variable);
      }
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiType returnType = method.getReturnType();
      if (returnType == null || returnType.getArrayDimensions() == 0) {
        return;
      }
      final PsiTypeElement typeElement = method.getReturnTypeElement();
      if (typeElement == null) {
        return;
      }
      final PsiType type = typeElement.getType();
      if (type.equals(returnType)) {
        return;
      }
      if (InspectionProjectProfileManager.isInformationLevel(getShortName(), method)) {
        registerError(typeElement, method);
        PsiElement child = method.getParameterList();
        PsiJavaToken first = null;
        PsiJavaToken last = null;
        while (!(child instanceof PsiCodeBlock)) {
          if (child instanceof PsiJavaToken) {
            final PsiJavaToken token = (PsiJavaToken)child;
            final IElementType tokenType = token.getTokenType();
            if (JavaTokenType.LBRACKET.equals(tokenType) || JavaTokenType.RBRACKET.equals(tokenType)) {
              if (first == null) first = token;
              last = token;
            }
          }
          child = child.getNextSibling();
        }
        if (first != null) registerErrorAtRange(first, last, method);
      }
      registerMethodError(method, method);
    }
  }
}