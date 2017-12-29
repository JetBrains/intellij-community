// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit2.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.visibility.EntryPointWithVisibilityLevel;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonProcessors;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class JUnitEntryPoint extends EntryPointWithVisibilityLevel {
  public boolean ADD_JUNIT_TO_ENTRIES = true;

  @NotNull
  public String getDisplayName() {
    return "JUnit test cases";
  }

  public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
    return isEntryPoint(psiElement);
  }

  @Override
  public boolean isEntryPoint(@NotNull PsiElement psiElement) {
    if (psiElement instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)psiElement;
      if (JUnitUtil.isTestClass(aClass, false, true)) {
        final boolean isJUnit5 = JUnitUtil.isJUnit5(aClass);
        if (!PsiClassUtil.isRunnableClass(aClass, !isJUnit5, true)) {
          final PsiClass topLevelClass = PsiTreeUtil.getTopmostParentOfType(aClass, PsiClass.class);
          if (topLevelClass != null && PsiClassUtil.isRunnableClass(topLevelClass, !isJUnit5, true)) {
            return true;
          }
          final CommonProcessors.FindProcessor<PsiClass> findProcessor = new CommonProcessors.FindProcessor<PsiClass>() {
            @Override
            protected boolean accept(PsiClass psiClass) {
              return !psiClass.hasModifierProperty(PsiModifier.ABSTRACT);
            }
          };
          return !ClassInheritorsSearch.search(aClass).forEach(findProcessor) && findProcessor.isFound();
        }
        return true;
      }
    }
    else if (psiElement instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)psiElement;
      if (method.isConstructor() && method.getParameterList().getParametersCount() == 0) {
        return JUnitUtil.isTestClass(method.getContainingClass());
      }
      if (JUnitUtil.isTestMethodOrConfig(method)) return true;
    }
    else if (psiElement instanceof PsiField) {
      return AnnotationUtil.isAnnotated((PsiField)psiElement, JUnitUtil.PARAMETRIZED_PARAMETER_ANNOTATION_NAME, 0);
    }
    return false;
  }

  @Override
  public int getMinVisibilityLevel(PsiMember member) {
    PsiClass container = null;
    if (member instanceof PsiClass) {
      container = (PsiClass)member;
    }
    else if (member instanceof PsiMethod) {
      container = member.getContainingClass();
    }
    if (container != null && JUnitUtil.isJUnit5TestClass(container, false)) {
      return PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
    }

    return -1;
  }

  @Override
  public String getTitle() {
    return "Suggest package-private visibility level for junit 5 tests";
  }

  @Override
  public String getId() {
    return "junit";
  }

  public boolean isSelected() {
    return ADD_JUNIT_TO_ENTRIES;
  }

  public void setSelected(boolean selected) {
    ADD_JUNIT_TO_ENTRIES = selected;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (!ADD_JUNIT_TO_ENTRIES) {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
  }

  @Override
  public String[] getIgnoreAnnotations() {
    return new String[]{"org.junit.Rule",
                        "org.junit.ClassRule",
                        "org.junit.experimental.theories.DataPoint",
                        "org.junit.experimental.theories.DataPoints"};
  }
}