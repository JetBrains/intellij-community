// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;

public final class JavaFxCanBeStaticExtension implements Condition<PsiElement> {
  @Override
  public boolean value(PsiElement psiElement) {
    return psiElement instanceof PsiModifierListOwner && 
           AnnotationUtil.isAnnotated((PsiModifierListOwner)psiElement, JavaFxCommonNames.JAVAFX_FXML_ANNOTATION, 0);
  }
}
