// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.junit2.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.visibility.EntryPointWithVisibilityLevel;
import com.intellij.execution.JUnitBundle;
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
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public final class JUnitEntryPoint extends EntryPointWithVisibilityLevel {
  private static final Collection<String> FIELD_ANNOTATIONS = Arrays.asList(JUnitUtil.PARAMETRIZED_PARAMETER_ANNOTATION_NAME, 
                                                                            JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION);
  public boolean ADD_JUNIT_TO_ENTRIES = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return JUnitBundle.message("unused.declaration.junit.test.entry.point");
  }

  @Override
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
          final CommonProcessors.FindProcessor<PsiClass> findProcessor = new CommonProcessors.FindProcessor<>() {
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
    else if (psiElement instanceof PsiMethod method) {
      if (method.isConstructor() && method.getParameterList().isEmpty()) {
        final PsiClass aClass = method.getContainingClass();
        return aClass != null && JUnitUtil.isTestClass(aClass);
      }
      if (JUnitUtil.isTestMethodOrConfig(method)) return true;
    }
    else if (psiElement instanceof PsiField) {
      return AnnotationUtil.isAnnotated((PsiField)psiElement, FIELD_ANNOTATIONS, 0);
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
    if (container != null && 
        JUnitUtil.isJUnit5TestClass(container, false) && 
        !JUnitUtil.isJUnit4TestClass(container, false) && 
        !JUnitUtil.isJUnit3TestClass(container)) {
      return PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
    }

    if (member instanceof PsiField &&
        AnnotationUtil.isAnnotated(member, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION, 0)) {
      return PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
    }

    return ACCESS_LEVEL_INVALID;
  }

  @Override
  public String getTitle() {
    return JUnitBundle.message("junit.entry.point.suggest.package.private.visibility.junit5");
  }

  @Override
  public String getId() {
    return "junit";
  }

  @Override
  public boolean isSelected() {
    return ADD_JUNIT_TO_ENTRIES;
  }

  @Override
  public void setSelected(boolean selected) {
    ADD_JUNIT_TO_ENTRIES = selected;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
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