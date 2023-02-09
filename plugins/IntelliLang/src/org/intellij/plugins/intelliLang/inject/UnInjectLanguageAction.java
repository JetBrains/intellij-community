// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.IntelliLangBundle;
import org.intellij.plugins.intelliLang.references.InjectedReferencesContributor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class UnInjectLanguageAction implements IntentionAction, LowPriorityAction {
  @Override
  @NotNull
  public String getText() {
    return IntelliLangBundle.message("intelliLang.uninject.language.action.text");
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return new IntentionPreviewInfo.Html(IntelliLangBundle.message("intelliLang.uninject.language.action.preview"));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = InjectedLanguageUtil.findInjectedPsiNoCommit(file, offset);
    if (element == null) {
      return InjectedReferencesContributor.isInjected(file.findReferenceAt(offset));
    }
    return element.getUserData(LanguageInjectionSupport.INJECTOR_SUPPORT) != null;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    ApplicationManager.getApplication().runReadAction(() -> invokeImpl(project, editor, file));
  }

  public static void invokeImpl(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    final PsiFile psiFile = InjectedLanguageUtil.findInjectedPsiNoCommit(file, offset);
    if (psiFile == null) {
      PsiReference reference = file.findReferenceAt(offset);
      if (reference == null) return;
      if (reference.getElement() instanceof PsiLanguageInjectionHost host) {
        for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
          if (support.isApplicableTo(host) && support.removeInjectionInPlace(host)) {
            PsiManager.getInstance(project).dropPsiCaches();
            return;
          }
        }
      }
      PsiElement element = reference.getElement();
      LanguageInjectionSupport support = element.getUserData(LanguageInjectionSupport.INJECTOR_SUPPORT);
      if (support != null) {
        if (support.removeInjection(element)) {
          PsiManager.getInstance(project).dropPsiCaches();
        }
      }
      return;
    }
    final PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(psiFile);
    if (host == null) return;
    final LanguageInjectionSupport support = psiFile.getUserData(LanguageInjectionSupport.INJECTOR_SUPPORT);
    if (support == null) return;
    try {
      if (!support.removeInjectionInPlace(host)) {
        defaultFunctionalityWorked(host);
      }
    }
    finally {
      FileContentUtil.reparseFiles(project, Collections.emptyList(), true);
    }
  }

  private static boolean defaultFunctionalityWorked(final PsiLanguageInjectionHost host) {
    Set<String> languages = new HashSet<>();
    List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(host.getProject()).getInjectedPsiFiles(host);
    if (files == null) return false;
    for (Pair<PsiElement, TextRange> pair : files) {
      for (Language lang = pair.first.getLanguage(); lang != null; lang = lang.getBaseLanguage()) {
        languages.add(lang.getID());
      }
    }
    // todo there is a problem: host i.e. literal expression is confused with "target" i.e. parameter
    // todo therefore this part doesn't work for java
    return Configuration.getProjectInstance(host.getProject()).setHostInjectionEnabled(host, languages, false);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

}
