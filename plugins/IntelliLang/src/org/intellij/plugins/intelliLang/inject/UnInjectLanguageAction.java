/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.references.InjectedReferencesContributor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class UnInjectLanguageAction implements IntentionAction, LowPriorityAction {

  @NotNull
  public String getText() {
    return "Un-inject Language/Reference";
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = InjectedLanguageUtil.findInjectedPsiNoCommit(file, offset);
    if (element == null) {
      return InjectedReferencesContributor.isInjected(file.findReferenceAt(offset));
    }
    return element.getUserData(LanguageInjectionSupport.INJECTOR_SUPPORT) != null;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    ApplicationManager.getApplication().runReadAction(() -> invokeImpl(project, editor, file));
  }

  public static void invokeImpl(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    final PsiFile psiFile = InjectedLanguageUtil.findInjectedPsiNoCommit(file, offset);
    if (psiFile == null) {
      PsiReference reference = file.findReferenceAt(offset);
      if (reference == null) return;
      if (reference.getElement() instanceof PsiLanguageInjectionHost) {
        PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)reference.getElement();
        for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
          if (support.isApplicableTo(host) && support.removeInjectionInPlace(host)) {
            ((PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker()).incCounter();
            return;
          }
        }
      }
      PsiElement element = reference.getElement();
      LanguageInjectionSupport support = element.getUserData(LanguageInjectionSupport.INJECTOR_SUPPORT);
      if (support != null) {
        if (support.removeInjection(element)) {
          ((PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker()).incCounter();
        }
      }
      return;
    }
    final PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(psiFile);
    if (host == null) return;
    final LanguageInjectionSupport support = psiFile.getUserData(LanguageInjectionSupport.INJECTOR_SUPPORT);
    if (support == null) return;
    try {
      if (psiFile.getUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE) != null) {
        // temporary injection
        TemporaryPlacesRegistry temporaryPlacesRegistry = TemporaryPlacesRegistry.getInstance(project);
        for (PsiLanguageInjectionHost.Shred shred : InjectedLanguageUtil.getShreds(psiFile)) {
          if (temporaryPlacesRegistry.removeHostWithUndo(project, shred.getHost())) break;
        }
      }
      else if (!support.removeInjectionInPlace(host)) {
        defaultFunctionalityWorked(host);
      }
    }
    finally {
      FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
    }
  }

  private static boolean defaultFunctionalityWorked(final PsiLanguageInjectionHost host) {
    final THashSet<String> languages = new THashSet<>();
    final List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(host.getProject()).getInjectedPsiFiles(host);
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

  public boolean startInWriteAction() {
    return false;
  }

}
