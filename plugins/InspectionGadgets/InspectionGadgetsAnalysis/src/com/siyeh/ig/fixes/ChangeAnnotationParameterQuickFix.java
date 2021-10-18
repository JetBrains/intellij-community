package com.siyeh.ig.fixes;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.command.undo.UndoUtil;
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
public class ChangeAnnotationParameterQuickFix extends LocalQuickFixOnPsiElement implements IntentionAction {
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
    if (null == myNewValue) {
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
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return isAvailable();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    applyFix();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
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
    UndoUtil.markPsiFileForUndo(file);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
