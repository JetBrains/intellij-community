/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.NormalizeDeclarationFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MultipleTypedDeclarationInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "multiple.typed.declaration.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "VariablesOfDifferentTypesInDeclaration";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "multiple.typed.declaration.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MultiplyTypedDeclarationVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new NormalizeDeclarationFix();
  }

  private static class MultiplyTypedDeclarationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitDeclarationStatement(
      PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      final PsiElement[] elements = statement.getDeclaredElements();
      if (elements.length > 1) {
        final PsiType baseType = ((PsiVariable)elements[0]).getType();
        boolean hasMultipleTypes = false;
        for (int i = 1; i < elements.length; i++) {
          final PsiLocalVariable var = (PsiLocalVariable)elements[i];
          final PsiType variableType = var.getType();
          if (!variableType.equals(baseType)) {
            hasMultipleTypes = true;
          }
        }
        if (hasMultipleTypes) {
          for (int i = 1; i < elements.length; i++) {
            final PsiLocalVariable var =
              (PsiLocalVariable)elements[i];
            registerVariableError(var);
          }
        }
      }
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (!childrenContainTypeElement(field)) {
        return;
      }
      final List<PsiField> fields = getSiblingFields(field);
      if (fields.size() > 1) {
        final PsiField firstField = fields.get(0);
        final PsiType baseType = firstField.getType();
        boolean hasMultipleTypes = false;
        for (int i = 1; i < fields.size(); i++) {
          final PsiField variable = fields.get(i);
          final PsiType variableType = variable.getType();
          if (!variableType.equals(baseType)) {
            hasMultipleTypes = true;
          }
        }
        if (hasMultipleTypes) {
          for (int i = 1; i < fields.size(); i++) {
            final PsiField var = fields.get(i);
            registerVariableError(var);
          }
        }
      }
    }

    public static List<PsiField> getSiblingFields(PsiField field) {
      final List<PsiField> out = new ArrayList<>(5);
      out.add(field);
      PsiField nextField =
        PsiTreeUtil.getNextSiblingOfType(field,
                                         PsiField.class);
      if (nextField != null) {
        PsiTypeElement nextTypeElement = nextField.getTypeElement();
        while (nextTypeElement != null &&
               nextTypeElement.equals(field.getTypeElement())) {
          out.add(nextField);
          nextField =
            PsiTreeUtil.getNextSiblingOfType(nextField,
                                             PsiField.class);
          if (nextField == null) {
            break;
          }
          nextTypeElement = nextField.getTypeElement();
        }
      }
      return out;
    }

    public static boolean childrenContainTypeElement(PsiElement field) {
      final PsiElement[] children = field.getChildren();
      for (PsiElement aChildren : children) {
        if (aChildren instanceof PsiTypeElement) {
          return true;
        }
      }
      return false;
    }
  }
}