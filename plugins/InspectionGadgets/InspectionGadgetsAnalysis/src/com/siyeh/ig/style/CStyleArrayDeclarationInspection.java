/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CStyleArrayDeclarationInspection extends BaseInspection {

  public boolean ignoreVariables = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "c.style.array.declaration.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Object info = infos[0];
    if (info instanceof PsiMethod) {
      return InspectionGadgetsBundle.message("cstyle.array.method.declaration.problem.descriptor");
    }
    final int choice = info instanceof PsiField ? 1 : info instanceof PsiParameter ? 2 : 3;
    return InspectionGadgetsBundle.message("cstyle.array.variable.declaration.problem.descriptor", Integer.valueOf(choice));
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Ignore C-style declarations in variables", this, "ignoreVariables");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new CStyleArrayDeclarationFix();
  }

  private static class CStyleArrayDeclarationFix
    extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "c.style.array.declaration.replace.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (element instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)element;
        variable.normalizeDeclaration();
        CodeStyleManager.getInstance(project).reformat(variable);
      }
      else if (element instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)element;
        final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        if (returnTypeElement == null) {
          return;
        }
        final PsiType returnType = method.getReturnType();
        if (returnType == null) {
          return;
        }
        PsiElement child = method.getParameterList();
        while (!(child instanceof PsiCodeBlock)) {
          final PsiElement element1 = child;
          child = child.getNextSibling();
          if (element1 instanceof PsiJavaToken) {
            final PsiJavaToken token = (PsiJavaToken)element1;
            final IElementType tokenType = token.getTokenType();
            if (JavaTokenType.LBRACKET.equals(tokenType) || JavaTokenType.RBRACKET.equals(tokenType)) {
              token.delete();
            }
          }
        }
        final PsiTypeElement typeElement = JavaPsiFacade.getElementFactory(project).createTypeElement(returnType);
        returnTypeElement.replace(typeElement);
      }
    }
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
      final PsiType declaredType = variable.getType();
      if (declaredType.getArrayDimensions() == 0) {
        return;
      }
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null) {
        return; // Could be true for enum constants.
      }
      final PsiType elementType = typeElement.getType();
      if (elementType.equals(declaredType)) {
        return;
      }
      registerVariableError(variable, variable);
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
      registerMethodError(method, method);
    }
  }
}