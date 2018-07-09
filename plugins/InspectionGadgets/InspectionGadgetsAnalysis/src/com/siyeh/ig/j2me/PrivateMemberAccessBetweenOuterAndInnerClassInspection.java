/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrivateMemberAccessBetweenOuterAndInnerClassInspection extends BaseInspection {

  @NotNull
  @Override
  public String getID() {
    return "SyntheticAccessorCall";
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "PrivateMemberAccessBetweenOuterAndInnerClass";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "private.member.access.between.outer.and.inner.classes.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message(
      "private.member.access.between.outer.and.inner.classes.problem.descriptor",
      aClass.getName());
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final String className = aClass.getName();
    if (infos.length == 1) {
      return new MakePackagePrivateFix(className, true);
    }
    final PsiMember member = (PsiMember)infos[1];
    @NonNls final String memberName;
    if (member instanceof PsiMethod) {
      memberName = member.getName() + "()";
    }
    else {
      memberName = member.getName();
    }
    @NonNls final String elementName = className + '.' + memberName;
    return new MakePackagePrivateFix(elementName, false);
  }

  private static class MakePackagePrivateFix extends InspectionGadgetsFix {

    private final String elementName;
    private final boolean constructor;

    private MakePackagePrivateFix(String elementName, boolean constructor) {
      this.elementName = elementName;
      this.constructor = constructor;
    }

    @Override
    @NotNull
    public String getName() {
      if (constructor) {
        return InspectionGadgetsBundle.message(
          "private.member.access.between.outer.and.inner.classes.make.constructor.package.local.quickfix",
          elementName);
      }
      return InspectionGadgetsBundle.message(
        "private.member.access.between.outer.and.inner.classes.make.local.quickfix",
        elementName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Make package-private";
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (constructor) {
        makeConstructorPackageLocal(project, element);
      }
      else {
        makeMemberPackageLocal(element);
      }
    }

    private static void makeMemberPackageLocal(PsiElement element) {
      final PsiElement parent = element.getParent();
      final PsiReferenceExpression reference =
        (PsiReferenceExpression)parent;
      final PsiModifierListOwner member =
        (PsiModifierListOwner)reference.resolve();
      if (member == null) {
        return;
      }
      final PsiModifierList modifiers = member.getModifierList();
      if (modifiers == null) {
        return;
      }
      modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
      modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
      modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
    }

    private static void makeConstructorPackageLocal(Project project, PsiElement element) {
      final PsiNewExpression newExpression =
        PsiTreeUtil.getParentOfType(element,
                                    PsiNewExpression.class);
      if (newExpression == null) {
        return;
      }
      final PsiMethod constructor =
        newExpression.resolveConstructor();
      if (constructor != null) {
        final PsiModifierList modifierList =
          constructor.getModifierList();
        modifierList.setModifierProperty(PsiModifier.PRIVATE,
                                         false);
        return;
      }
      final PsiJavaCodeReferenceElement referenceElement =
        (PsiJavaCodeReferenceElement)element;
      final PsiElement target = referenceElement.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      final PsiElementFactory elementFactory =
        JavaPsiFacade.getElementFactory(project);
      final PsiMethod newConstructor = elementFactory.createConstructor();
      final PsiModifierList modifierList =
        newConstructor.getModifierList();
      modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
      aClass.add(newConstructor);
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    if (FileTypeUtils.isInServerPageFile(file)) {
      // disable for jsp files IDEADEV-12957
      return false;
    }
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PrivateMemberAccessFromInnerClassVisitor();
  }

  private static class PrivateMemberAccessFromInnerClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (expression.getType() instanceof PsiArrayType) {
        return;
      }
      final PsiClass containingClass = ClassUtils.getContainingClass(expression);
      if (containingClass == null) {
        return;
      }
      final JavaResolveResult resolveResult = expression.resolveMethodGenerics();
      if (!resolveResult.isAccessible()) {
        return;
      }
      final PsiMethod constructor = (PsiMethod)resolveResult.getElement();
      if (constructor == null) {
        final PsiJavaCodeReferenceElement classReference =
          expression.getClassOrAnonymousClassReference();
        if (classReference == null) {
          return;
        }
        final PsiElement target = classReference.resolve();
        if (!(target instanceof PsiClass)) {
          return;
        }
        final PsiClass aClass = (PsiClass)target;
        if (!aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
        if (aClass.equals(containingClass)) {
          return;
        }
        registerNewExpressionError(expression, aClass);
      }
      else {
        if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
        final PsiClass aClass = constructor.getContainingClass();
        if (containingClass.equals(aClass)) {
          return;
        }
        registerNewExpressionError(expression, aClass);
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement referenceNameElement = expression.getReferenceNameElement();
      if (referenceNameElement == null) {
        return;
      }
      final JavaResolveResult resolveResult = expression.advancedResolve(false);
      if (!resolveResult.isAccessible()) {
        return;
      }
      final PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiMethod || element instanceof PsiField)) {
        return;
      }
      final PsiMember member = (PsiMember)element;
      if (!member.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final Object value = ExpressionUtils.computeConstantExpression(expression);
      if (value != null) {
        return; // no synthetic accessor created, compile time constant will be inlined by javac
      }
      final PsiElement containingClass = ClassUtils.getContainingClass(expression);
      if (containingClass == null) {
        return;
      }
      final PsiClass memberClass = ClassUtils.getContainingClass(member);
      if (memberClass == null || memberClass.equals(containingClass)) {
        return;
      }
      registerError(referenceNameElement, memberClass, member);
    }
  }
}
