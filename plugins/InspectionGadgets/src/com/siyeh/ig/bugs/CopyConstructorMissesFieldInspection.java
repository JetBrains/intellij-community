// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtilRt;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Bas Leijdekkers
 */
public class CopyConstructorMissesFieldInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("copy.constructor.misses.field.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final List<PsiField> fields = (List<PsiField>)infos[0];
    if (fields.size() == 1) {
      return InspectionGadgetsBundle.message("copy.constructor.misses.field.problem.descriptor.1", fields.get(0).getName());
    }
    else if (fields.size() == 2) {
      return InspectionGadgetsBundle.message("copy.constructor.misses.field.problem.descriptor.2",
                                             fields.get(0).getName(), fields.get(1).getName());
    }
    else if (fields.size() == 3) {
      return InspectionGadgetsBundle.message("copy.constructor.misses.field.problem.descriptor.3",
                                             fields.get(0).getName(), fields.get(1).getName(), fields.get(2).getName());
    }
    System.out.println("fields = " + fields);
    return InspectionGadgetsBundle.message("copy.constructor.misses.field.problem.descriptor.many", fields.size());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CopyConstructorMissesFieldVisitor();
  }

  private static class CopyConstructorMissesFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      if (!MethodUtils.isCopyConstructor(method)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final List<PsiField> fields = Arrays.stream(aClass.getFields())
        .filter(f -> !f.hasModifierProperty(PsiModifier.STATIC)
                     && !f.hasModifierProperty(PsiModifier.TRANSIENT)
                     && (!f.hasModifierProperty(PsiModifier.FINAL) || f.getInitializer() == null))
        .collect(Collectors.toList());
      if (fields.isEmpty()) return;
      final PsiParameter parameter = method.getParameterList().getParameters()[0];
      final List<PsiField> assignedFields = new SmartList<>();
      final Set<PsiMethod> methodsOneLevelDeep = new HashSet<>();
      if (!PsiTreeUtil.processElements(method, e -> collectAssignedFields(e, parameter, methodsOneLevelDeep, assignedFields))) {
        return;
      }
      for (PsiMethod calledMethod : methodsOneLevelDeep) {
        if (!PsiTreeUtil.processElements(calledMethod, e -> collectAssignedFields(e, parameter, null, assignedFields))) {
          return;
        };
      }
      for (PsiField assignedField : assignedFields) {
        if (aClass == PsiUtil.resolveClassInClassTypeOnly(assignedField.getType())) {
          return;
        }
      }
      fields.removeAll(assignedFields);
      if (fields.isEmpty()) {
        return;
      }
      registerMethodError(method, fields);
    }

    private static boolean collectAssignedFields(PsiElement element, PsiParameter parameter,
                                                 @Nullable Set<PsiMethod> methods, List<PsiField> assignedFields) {
      if (element instanceof PsiAssignmentExpression) {
        final PsiExpression lhs = ParenthesesUtils.stripParentheses(((PsiAssignmentExpression)element).getLExpression());
        final PsiVariable variable = resolveVariable(lhs, null);
        if (variable instanceof PsiField) {
          assignedFields.add((PsiField)variable);
        }
      }
      else if (RefactoringChangeUtil.isSuperOrThisMethodCall(element)) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        for (PsiExpression argument : methodCallExpression.getArgumentList().getExpressions()) {
          argument = ParenthesesUtils.stripParentheses(argument);
          final PsiVariable variable = resolveVariable(argument, parameter);
          if (variable == parameter) {
            // instance to copy is passed to another constructor
            return false;
          }
          if (variable instanceof PsiField) {
            assignedFields.add((PsiField)variable);
          }
          ContainerUtilRt.addIfNotNull(assignedFields, resolveFieldOfGetter(argument, parameter));
        }
      }
      else if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        final PsiExpression qualifier =
          ParenthesesUtils.stripParentheses(methodCallExpression.getMethodExpression().getQualifierExpression());
        if (qualifier == null || qualifier instanceof PsiThisExpression) {
          final PsiMethod method = methodCallExpression.resolveMethod();
          final PsiField field = PropertyUtil.getFieldOfSetter(method);
          if (field != null) {
            // field assigned using setter
            assignedFields.add(field);
          }
          else if (methods != null && method != null) {
            methods.add(method);
          }
        }
        else if (qualifier instanceof PsiReferenceExpression) {
          // consider field assigned if method is called on it.
          final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
          final PsiElement target = referenceExpression.resolve();
          if (target instanceof PsiField) {
            assignedFields.add((PsiField)target);
          }
        }
      }
      return true;
    }

    private static PsiVariable resolveVariable(PsiExpression expression, PsiParameter requiredQualifier) {
      if (!(expression instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(referenceExpression.getQualifierExpression());
      final PsiElement target = referenceExpression.resolve();
      if (requiredQualifier == null) {
        if (!(qualifier == null || qualifier instanceof PsiThisExpression)) {
          return null;
        }
      }
      else if (!ExpressionUtils.isReferenceTo(qualifier, requiredQualifier)) {
        return target == requiredQualifier ? requiredQualifier : null;
      }
      return target instanceof PsiVariable ? (PsiVariable)target : null;
    }

    private static PsiField resolveFieldOfGetter(PsiExpression expression, PsiParameter requiredQualifier) {
      if (!(expression instanceof PsiMethodCallExpression)) {
        return null;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
      if (!ExpressionUtils.isReferenceTo(qualifier, requiredQualifier)) {
        return null;
      }
      return PropertyUtil.getFieldOfGetter(methodCallExpression.resolveMethod());
    }
  }
}
