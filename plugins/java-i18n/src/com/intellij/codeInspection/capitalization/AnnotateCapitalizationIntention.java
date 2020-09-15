// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.capitalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class AnnotateCapitalizationIntention implements IntentionAction {

  public AnnotateCapitalizationIntention() {
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiModifierListOwner element = getElement(editor, file);
    if (element == null ||
        (!ApplicationManager.getApplication().isUnitTestMode() && BaseIntentionAction.canModify(element)) ||
        AnnotationUtil.findAnnotation(element, Nls.class.getName()) != null) return false;
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaI18nBundle.message("intention.family.annotate.capitalization.type");
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiModifierListOwner modifierListOwner = getElement(editor, file);
    if (modifierListOwner == null) throw new IncorrectOperationException();

    BaseListPopupStep<Nls.Capitalization> step =
      new BaseListPopupStep<>(null, Nls.Capitalization.Title, Nls.Capitalization.Sentence) {
        @Override
        public PopupStep onChosen(final Nls.Capitalization selectedValue, boolean finalChoice) {
          WriteCommandAction.writeCommandAction(project).run(() -> {
            String nls = Nls.class.getName();
            PsiAnnotation annotation = JavaPsiFacade.getInstance(project).getElementFactory()
              .createAnnotationFromText("@" + nls + "(capitalization = " +
                                        nls + ".Capitalization." + selectedValue.toString() + ")",
                                        modifierListOwner);
            new AddAnnotationFix(Nls.class.getName(), modifierListOwner, annotation.getParameterList().getAttributes()).applyFix();
          });
          return FINAL_CHOICE;
        }
      };
    JBPopupFactory.getInstance().createListPopup(step).showInBestPositionFor(editor);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  private static PsiModifierListOwner getElement(Editor editor, PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
    if (parameter == null) return null;
    PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
    if (method == null) return null;
    PsiType type = parameter.getType();
    return type.equalsToText(CommonClassNames.JAVA_LANG_STRING) ? parameter : null;
  }
}
