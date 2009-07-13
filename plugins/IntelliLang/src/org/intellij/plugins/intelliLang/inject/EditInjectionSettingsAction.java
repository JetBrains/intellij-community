/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.intellij.plugins.intelliLang.inject;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.FileContentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.intention.IntentionAction;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;
import org.intellij.plugins.intelliLang.inject.config.ui.AbstractInjectionPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.XmlTagPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.XmlAttributePanel;
import org.intellij.plugins.intelliLang.inject.config.ui.MethodParameterPanel;
import static org.intellij.plugins.intelliLang.inject.InjectLanguageAction.findInjectionHost;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class EditInjectionSettingsAction implements IntentionAction {

  @NotNull
  public String getText() {
    return "Edit Language Injection Settings";
  }

  @NotNull
  public String getFamilyName() {
    return InjectLanguageAction.INJECT_LANGUAGE_FAMILY;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) {
      return false;
    }
    List<Pair<PsiElement, TextRange>> injectedPsi = host.getInjectedPsi();
    return injectedPsi != null && !injectedPsi.isEmpty();
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    final Configuration configuration = Configuration.getInstance();
    final ArrayList<BaseInjection> injections = new ArrayList<BaseInjection>();
    final ArrayList<PsiAnnotation> annotations = new ArrayList<PsiAnnotation>();
    UnInjectLanguageAction.collectInjections(host, configuration, injections, annotations);
    if (!annotations.isEmpty() || injections.isEmpty()) return;
    final BaseInjection injection = injections.get(0);
    final AbstractInjectionPanel panel;
    if (injection instanceof XmlTagInjection) {
      panel = new XmlTagPanel((XmlTagInjection)injection, project);
    }
    else if (injection instanceof XmlAttributeInjection) {
      panel = new XmlAttributePanel((XmlAttributeInjection)injection, project);
    }
    else if (injection instanceof MethodParameterInjection) {
      panel = new MethodParameterPanel((MethodParameterInjection)injection, project);
    }
    else {
      throw new AssertionError(injection);
    }
    panel.reset();
    final DialogBuilder builder = new DialogBuilder(project);
    builder.addOkAction();
    builder.addCancelAction();
    builder.setCenterPanel(panel.getComponent());
    builder.setTitle(getText());
    builder.setOkOperation(new Runnable() {
      public void run() {
        panel.apply();
        configuration.configurationModified();
        FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
        builder.getWindow().dispose();
      }
    });
    builder.show();
  }

  public boolean startInWriteAction() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }
}