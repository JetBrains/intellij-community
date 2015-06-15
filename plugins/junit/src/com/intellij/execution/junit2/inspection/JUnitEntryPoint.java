/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 28-May-2007
 */
package com.intellij.execution.junit2.inspection;

import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonProcessors;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class JUnitEntryPoint extends EntryPoint {
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
    if (ADD_JUNIT_TO_ENTRIES) {
      if (psiElement instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)psiElement;
        if (JUnitUtil.isTestClass(aClass, false, true)) {
          if (!PsiClassUtil.isRunnableClass(aClass, true, true)) {
            final PsiClass topLevelClass = PsiTreeUtil.getTopmostParentOfType(aClass, PsiClass.class);
            if (topLevelClass != null && PsiClassUtil.isRunnableClass(topLevelClass, true, true)) {
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
    }
    return false;
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
                        "org.mockito.Mock",
                        "org.mockito.Spy",
                        "org.mockito.Captor",
                        "org.mockito.InjectMocks",
                        "org.junit.ClassRule",
                        "org.junit.experimental.theories.DataPoint"};
  }
}