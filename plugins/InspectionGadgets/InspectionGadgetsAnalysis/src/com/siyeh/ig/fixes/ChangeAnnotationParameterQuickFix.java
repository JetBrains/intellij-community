// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public class ChangeAnnotationParameterQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myName;
  private final String myNewValue;

  public ChangeAnnotationParameterQuickFix(@NotNull PsiAnnotation annotation, @NotNull String name, @Nullable String newValue) {
    super(annotation);
    myName = name;
    myNewValue = newValue;
  }

  @Override
  @NotNull
  @IntentionName
  public String getText() {
    if (myNewValue == null) {
      return InspectionGadgetsBundle.message("remove.annotation.parameter.0.fix.name", myName);
    }
    return InspectionGadgetsBundle.message("set.annotation.parameter.0.1.fix.name", myName, myNewValue);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiAnnotation annotation = (PsiAnnotation)startElement;
    final PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotation, myName);
    if (myNewValue != null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      final PsiAnnotation dummyAnnotation = elementFactory.createAnnotationFromText("@A" + "(" + myName + "=" + myNewValue + ")", null);
      annotation.setDeclaredAttributeValue(myName, dummyAnnotation.getParameterList().getAttributes()[0].getValue());
    }
    else if (attribute != null) {
      new CommentTracker().deleteAndRestoreComments(attribute);
    }
  }
}
