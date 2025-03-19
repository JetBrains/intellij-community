// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

public final class JavaFxScopeEnlarger extends UseScopeEnlarger {
  @Override
  public @Nullable SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
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
           AnnotationUtil.isAnnotated(field, JavaFxCommonNames.JAVAFX_FXML_ANNOTATION, 0);
  }

  private static boolean needToEnlargeMethodScope(PsiMethod method) {
    final boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    return isStatic && method.getParameterList().getParametersCount() == 2 && PropertyUtilBase.hasAccessorName(method) &&
           InheritanceUtil.isInheritor(method.getParameterList().getParameters()[0].getType(), JavaFxCommonNames.JAVAFX_SCENE_NODE) ||
           !isStatic && !method.hasModifierProperty(PsiModifier.PUBLIC) &&
           AnnotationUtil.isAnnotated(method, JavaFxCommonNames.JAVAFX_FXML_ANNOTATION, 0);
  }

  public static final class GlobalFxmlSearchScope extends DelegatingGlobalSearchScope {
    public GlobalFxmlSearchScope(GlobalSearchScope baseScope) {
      super(baseScope);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return JavaFxFileTypeFactory.isFxml(file) && super.contains(file);
    }
  }
}
