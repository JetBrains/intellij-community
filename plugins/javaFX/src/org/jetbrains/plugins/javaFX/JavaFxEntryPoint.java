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

package org.jetbrains.plugins.javaFX;

import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

public class JavaFxEntryPoint extends EntryPoint {
  public static final String INITIALIZE_METHOD_NAME = "initialize";
  public boolean ADD_JAVAFX_TO_ENTRIES = true;

  @NotNull
  public String getDisplayName() {
    return "JavaFX Applications";
  }

  public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
    return isEntryPoint(psiElement);
  }

  @Override
  public boolean isEntryPoint(@NotNull PsiElement psiElement) {
    if (psiElement instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)psiElement;
      final int paramsCount = method.getParameterList().getParameters().length;
      final String methodName = method.getName();
      final PsiClass containingClass = method.getContainingClass();
      if (paramsCount == 1 &&
          PsiType.VOID.equals(method.getReturnType()) &&
          "start".equals(methodName)) {
        return InheritanceUtil.isInheritor(containingClass, true, JavaFxCommonNames.JAVAFX_APPLICATION_APPLICATION);
      }
      if (paramsCount == 0 && INITIALIZE_METHOD_NAME.equals(methodName) &&
          method.hasModifierProperty(PsiModifier.PUBLIC) &&
          containingClass != null &&
          JavaFxPsiUtil.isControllerClass(containingClass)) {
        return true;
      }

    }
    else if (psiElement instanceof PsiClass) {
      return InheritanceUtil.isInheritor((PsiClass)psiElement, true, JavaFxCommonNames.JAVAFX_APPLICATION_APPLICATION);
    }
    return false;
  }

  public boolean isSelected() {
    return ADD_JAVAFX_TO_ENTRIES;
  }

  public void setSelected(boolean selected) {
    ADD_JAVAFX_TO_ENTRIES = selected;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (!ADD_JAVAFX_TO_ENTRIES) {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
  }

  @Override
  public String[] getIgnoreAnnotations() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }
}