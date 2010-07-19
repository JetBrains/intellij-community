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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.IncorrectOperationException;
import static org.intellij.plugins.intelliLang.inject.InjectLanguageAction.findInjectionHost;

import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.InjectionsSettingsUI;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class EditInjectionSettingsAction implements IntentionAction {
  public static final String EDIT_INJECTION_TITLE = "Language Injection Settings";

  @NotNull
  public String getText() {
    return EDIT_INJECTION_TITLE;
  }

  @NotNull
  public String getFamilyName() {
    return "Edit Injection Settings";
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) return false;
    final List<Pair<PsiElement, TextRange>> injectedPsi = InjectedLanguageUtil.getInjectedPsiFiles(host);
    return injectedPsi != null && !injectedPsi.isEmpty();
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        invokeImpl(project, editor, file);
      }
    });
  }

  private static void invokeImpl(Project project, Editor editor, PsiFile file) {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) return;
    try {
      for (LanguageInjectionSupport support : Extensions.getExtensions(LanguageInjectionSupport.EP_NAME)) {
        if (support.editInjectionInPlace(host)) return;
      }
      ShowSettingsUtil.getInstance().editConfigurable(project, new InjectionsSettingsUI(project, Configuration.getInstance()));
    }
    finally {
      FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}