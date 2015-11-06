/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 */
public class DevKitImplicitUsageProvider implements ImplicitUsageProvider {

  @Override
  public boolean isImplicitUsage(PsiElement element) {
    if (element instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)element;
      return isDomElementClass(psiClass);
    }

    if (element instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)element;
      return isDomElementMethod(psiMethod);
    }

    return false;
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
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
    if (!(returnType instanceof PsiClassType)) {
      return false;
    }

    PsiClassType returnClassType = (PsiClassType)returnType;

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
    if (!PsiType.VOID.equals(method.getReturnType()) ||
        !method.getName().startsWith("visit") ||
        method.getParameterList().getParametersCount() != 1 ||
        !InheritanceUtil.isInheritor(containingClass, "com.intellij.util.xml.DomElementVisitor")) {
      return false;
    }

    final PsiType psiType = method.getParameterList().getParameters()[0].getType();
    return isDomElementInheritor(psiType);
  }

  private static boolean isDomElementInheritor(@Nullable PsiType psiType) {
    return InheritanceUtil.isInheritor(psiType, "com.intellij.util.xml.DomElement");
  }

  private static boolean isDomElementInheritor(@Nullable PsiClass psiClass) {
    return InheritanceUtil.isInheritor(psiClass, "com.intellij.util.xml.DomElement");
  }
}