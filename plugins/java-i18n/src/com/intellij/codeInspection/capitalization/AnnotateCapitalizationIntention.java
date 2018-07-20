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
package com.intellij.codeInspection.capitalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.IntentionAction;
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
        (!ApplicationManager.getApplication().isUnitTestMode() && element.getManager().isInProject(element)) ||
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
    return "Annotate capitalization type";
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiModifierListOwner modifierListOwner = getElement(editor, file);
    if (modifierListOwner == null) throw new IncorrectOperationException();

    BaseListPopupStep<Nls.Capitalization> step =
      new BaseListPopupStep<Nls.Capitalization>(null, Nls.Capitalization.Title, Nls.Capitalization.Sentence) {
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
