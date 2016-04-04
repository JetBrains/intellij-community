/*
 * Copyright 2005-2015 Bas Leijdekkers
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class RedundantMethodOverrideInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "redundant.method.override.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "redundant.method.override.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RedundantMethodOverrideFix();
  }

  private static class RedundantMethodOverrideFix
    extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "redundant.method.override.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement method = methodNameIdentifier.getParent();
      assert method != null;
      deleteElement(method);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantMethodOverrideVisitor();
  }

  private static class RedundantMethodOverrideVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final PsiMethod superMethod = MethodUtils.getSuper(method);
      if (superMethod == null) {
        return;
      }
      final PsiCodeBlock superBody = superMethod.getBody();
      if (superBody == null) {
        return;
      }
      if (!modifierListsAreEquivalent(method.getModifierList(), superMethod.getModifierList())) {
        return;
      }
      final PsiType superReturnType = superMethod.getReturnType();
      if (superReturnType == null || !superReturnType.equals(method.getReturnType())) {
        return;
      }
      if (!EquivalenceChecker.codeBlocksAreEquivalent(body, superBody)) {
        return;
      }
      registerMethodError(method);
    }

    private static boolean modifierListsAreEquivalent(@Nullable PsiModifierList list1, @Nullable PsiModifierList list2) {
      if (list1 == null) {
        return list2 == null;
      }
      else if (list2 == null) {
        return false;
      }
      final Set<String> annotations1 = new HashSet();
      for (PsiAnnotation annotation : list1.getAnnotations()) {
        annotations1.add(annotation.getQualifiedName());
      }
      final Set<String> annotations2 = new HashSet();
      for (PsiAnnotation annotation : list2.getAnnotations()) {
        annotations2.add(annotation.getQualifiedName());
      }
      final Set<String> uniques = disjunction(annotations1, annotations2);
      uniques.remove(CommonClassNames.JAVA_LANG_OVERRIDE);
      if (!uniques.isEmpty()) {
        return false;
      }
      return list1.hasModifierProperty(PsiModifier.STRICTFP) == list2.hasModifierProperty(PsiModifier.STRICTFP) &&
             list1.hasModifierProperty(PsiModifier.SYNCHRONIZED) == list2.hasModifierProperty(PsiModifier.SYNCHRONIZED) &&
             list1.hasModifierProperty(PsiModifier.PUBLIC) == list2.hasModifierProperty(PsiModifier.PUBLIC) &&
             list1.hasModifierProperty(PsiModifier.PROTECTED) == list2.hasModifierProperty(PsiModifier.PROTECTED);
    }

    private static <T> Set<T> disjunction(Collection<T> set1, Collection<T> set2) {
      final Set<T> result = new HashSet();
      for (T t : set1) {
        if (!set2.contains(t)) {
          result.add(t);
        }
      }
      for (T t : set2) {
        if (!set1.contains(t)) {
          result.add(t);
        }
      }
      return result;
    }
  }
}