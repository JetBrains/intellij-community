// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseSwitchFix implements LocalQuickFix, IntentionAction {
  protected final SmartPsiElementPointer<PsiSwitchBlock> myBlock;

  public BaseSwitchFix(@NotNull PsiSwitchBlock block) {
    myBlock = SmartPointerManager.createPointer(block);
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    invoke();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    invoke();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  abstract protected void invoke();

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiSwitchBlock startSwitch = myBlock.getElement();
    if (startSwitch == null) return false;
    int offset = Math.min(editor.getCaretModel().getOffset(), startSwitch.getTextRange().getEndOffset() - 1);
    PsiSwitchBlock currentSwitch = PsiTreeUtil.getNonStrictParentOfType(file.findElementAt(offset), PsiSwitchBlock.class);
    return currentSwitch == startSwitch;
  }

  @Nullable
  static Editor prepareForTemplateAndObtainEditor(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    Project project = element.getProject();
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) return null;
    Document document = editor.getDocument();
    PsiFile topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
    if (topLevelFile == null || document != topLevelFile.getViewProvider().getDocument()) return null;
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    return editor;
  }
}
