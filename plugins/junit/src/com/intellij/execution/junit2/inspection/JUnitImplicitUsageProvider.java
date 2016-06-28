/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.junit2.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;

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
    return isImplicitWrite(element);
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    if (element instanceof PsiParameter) {
      return AnnotationUtil.isAnnotated((PsiParameter)element, MOCK, false);
    }
    return element instanceof PsiField && AnnotationUtil.isAnnotated((PsiField) element, INJECTED_FIELD_ANNOTATIONS);
  }
}
