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
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

public class JavaFxScopeEnlarger extends UseScopeEnlarger {
  @Nullable
  @Override
  public SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
    PsiClass containingClass = null;
    if (element instanceof PsiField) {
      containingClass = ((PsiField)element).getContainingClass();
    }
    else if (element instanceof PsiMethod) {
      containingClass = ((PsiMethod)element).getContainingClass();
    }
    else if (element instanceof PsiParameter) {
      final PsiElement declarationScope = ((PsiParameter)element).getDeclarationScope();
      if (declarationScope instanceof PsiMethod && PropertyUtilBase.isSimplePropertySetter((PsiMethod)declarationScope)) {
        containingClass = ((PsiMethod)declarationScope).getContainingClass();
      }
    }

    if (containingClass != null) {
      if (element instanceof PsiField && needToEnlargeFieldScope((PsiField)element) ||
          element instanceof PsiMethod && needToEnlargeMethodScope((PsiMethod)element) ||
          element instanceof PsiParameter) {
        if (InheritanceUtil.isInheritor(containingClass, JavaFxCommonNames.JAVAFX_SCENE_NODE) ||
            JavaFxPsiUtil.isControllerClass(containingClass)) {
          final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(element.getProject());
          return new GlobalFxmlSearchScope(projectScope);
        }
      }
    }

    return null;
  }

  private static boolean needToEnlargeFieldScope(PsiField field) {
    return !field.hasModifierProperty(PsiModifier.PUBLIC) &&
           AnnotationUtil.isAnnotated(field, JavaFxCommonNames.JAVAFX_FXML_ANNOTATION, false);
  }

  private static boolean needToEnlargeMethodScope(PsiMethod method) {
    final boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    return isStatic && method.getParameterList().getParametersCount() == 2 &&
           InheritanceUtil.isInheritor(method.getParameterList().getParameters()[0].getType(), JavaFxCommonNames.JAVAFX_SCENE_NODE) ||
           !isStatic && !method.hasModifierProperty(PsiModifier.PUBLIC) &&
           AnnotationUtil.isAnnotated(method, JavaFxCommonNames.JAVAFX_FXML_ANNOTATION, false);
  }

  public static class GlobalFxmlSearchScope extends DelegatingGlobalSearchScope {
    public GlobalFxmlSearchScope(GlobalSearchScope baseScope) {
      super(baseScope);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return JavaFxFileTypeFactory.isFxml(file) && super.contains(file);
    }
  }
}
