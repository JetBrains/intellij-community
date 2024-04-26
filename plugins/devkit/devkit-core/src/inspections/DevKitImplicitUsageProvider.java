// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class DevKitImplicitUsageProvider implements ImplicitUsageProvider {

  @Override
  public boolean isImplicitUsage(@NotNull PsiElement element) {
    if (element instanceof PsiClass psiClass) {
      return isDomElementClass(psiClass);
    }

    if (element instanceof PsiMethod psiMethod) {
      return isDomElementMethod(psiMethod);
    }

    return false;
  }

  @Override
  public boolean isImplicitRead(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(@NotNull PsiElement element) {
    return false;
  }

  static boolean isDomElementClass(PsiClass psiClass) {
    if (psiClass.isEnum() ||
        psiClass.isAnnotationType() ||
        psiClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false;
    }

    return isDomElementInheritor(psiClass);
  }

  private static boolean isDomElementMethod(PsiMethod psiMethod) {
    if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC) ||
        psiMethod.hasModifierProperty(PsiModifier.STATIC) ||
        psiMethod.isConstructor() ||
        psiMethod.getParameterList().getParametersCount() > 1) {
      return false;
    }

    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) {
      return false;
    }

    if (!isDomElementClass(containingClass)) {
      return isDomElementVisitorMethod(psiMethod, containingClass);
    }

    final PsiType returnType = psiMethod.getReturnType();
    if (!(returnType instanceof PsiClassType returnClassType)) {
      return false;
    }

    // Dom getDom(), GenericAttributeValue<X> getAttr(), ...
    final PsiClass returnResolved = returnClassType.resolve();
    if (isDomElementInheritor(returnResolved)) {
      return true;
    }

    // List<Dom> getDoms()
    if (returnClassType.getParameterCount() == 1 &&
        InheritanceUtil.isInheritor(returnResolved, CommonClassNames.JAVA_UTIL_LIST)) {
      final PsiType listType = returnClassType.getParameters()[0];
      return isDomElementInheritor(listType);
    }

    return false;
  }

  private static boolean isDomElementVisitorMethod(PsiMethod method,
                                                   PsiClass containingClass) {
    if (!PsiTypes.voidType().equals(method.getReturnType()) ||
        !method.getName().startsWith("visit") ||
        method.getParameterList().getParametersCount() != 1 ||
        !InheritanceUtil.isInheritor(containingClass, "com.intellij.util.xml.DomElementVisitor")) {
      return false;
    }

    final PsiType psiType = Objects.requireNonNull(method.getParameterList().getParameter(0)).getType();
    return isDomElementInheritor(psiType);
  }

  private static boolean isDomElementInheritor(@Nullable PsiType psiType) {
    return InheritanceUtil.isInheritor(psiType, "com.intellij.util.xml.DomElement");
  }

  private static boolean isDomElementInheritor(@Nullable PsiClass psiClass) {
    return InheritanceUtil.isInheritor(psiClass, "com.intellij.util.xml.DomElement");
  }
}