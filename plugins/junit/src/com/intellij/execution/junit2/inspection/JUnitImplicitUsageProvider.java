// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit2.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class JUnitImplicitUsageProvider implements ImplicitUsageProvider {
  private static final String MOCK = "org.mockito.Mock";
  private static final List<String> INJECTED_FIELD_ANNOTATIONS = Arrays.asList(
    MOCK,
    "org.mockito.Spy",
    "org.mockito.Captor",
    "org.mockito.InjectMocks");


  @Override
  public boolean isImplicitUsage(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    return element instanceof PsiParameter && AnnotationUtil.isAnnotated((PsiParameter)element, MOCK, 0) ||
           element instanceof PsiField && AnnotationUtil.isAnnotated((PsiField)element, INJECTED_FIELD_ANNOTATIONS, 0);
  }

  @Override
  public boolean isImplicitlyNotNullInitialized(@NotNull PsiElement element) {
    return isImplicitWrite(element);
  }
}
