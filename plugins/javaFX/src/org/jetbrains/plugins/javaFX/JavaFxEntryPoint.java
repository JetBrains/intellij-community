// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.javaFX;

import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

public final class JavaFxEntryPoint extends EntryPoint {
  public static final String INITIALIZE_METHOD_NAME = "initialize";
  public boolean ADD_JAVAFX_TO_ENTRIES = true;

  @Override
  public @NotNull String getDisplayName() {
    return JavaFXBundle.message("javafx.entry.point.javafx.app");
  }

  @Override
  public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
    return isEntryPoint(psiElement);
  }

  @Override
  public boolean isEntryPoint(@NotNull PsiElement psiElement) {
    if (psiElement instanceof PsiMethod method) {
      final int paramsCount = method.getParameterList().getParametersCount();
      final String methodName = method.getName();
      final PsiClass containingClass = method.getContainingClass();
      if (paramsCount == 1 &&
          PsiTypes.voidType().equals(method.getReturnType()) &&
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

  @Override
  public boolean isSelected() {
    return ADD_JAVAFX_TO_ENTRIES;
  }

  @Override
  public void setSelected(boolean selected) {
    ADD_JAVAFX_TO_ENTRIES = selected;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (!ADD_JAVAFX_TO_ENTRIES) {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
  }

  @Override
  public String[] getIgnoreAnnotations() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }
}