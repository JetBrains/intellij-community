// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.intelliLang.InjectionsSettingsUI;
import org.intellij.plugins.intelliLang.IntelliLangBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author Gregory.Shrago
 */
final class EditInjectionSettingsAction implements IntentionAction, LowPriorityAction {

  @Override
  public @NotNull String getText() {
    return IntelliLangBundle.message("intention.name.language.injection.settings");
  }

  @Override
  public @NotNull String getFamilyName() {
    return IntelliLangBundle.message("intention.family.name.edit.injection.settings");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiFile psiFile = InjectedLanguageUtil.findInjectedPsiNoCommit(file, offset);
    if (psiFile == null) return false;
    final LanguageInjectionSupport support = psiFile.getUserData(LanguageInjectionSupport.SETTINGS_EDITOR);
    return support != null;
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile psiFile) throws IncorrectOperationException {
    ApplicationManager.getApplication().runReadAction(() -> invokeImpl(project, editor, psiFile));
  }

  private static void invokeImpl(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiFile psiFile = InjectedLanguageUtil.findInjectedPsiNoCommit(file, editor.getCaretModel().getOffset());
    if (psiFile == null) return;
    final PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(psiFile);
    if (host == null) return;
    final LanguageInjectionSupport support = psiFile.getUserData(LanguageInjectionSupport.SETTINGS_EDITOR);
    if (support == null) return;
    try {
      if (!support.editInjectionInPlace(host)) {
        ShowSettingsUtil.getInstance().editConfigurable(project, new InjectionsSettingsUI(project));
      }
    }
    finally {
      FileContentUtil.reparseFiles(project, Collections.emptyList(), true);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}