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

import com.intellij.lang.Language;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.FileContentUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.AbstractTagInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;
import org.intellij.plugins.intelliLang.inject.config.ui.AbstractInjectionPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.XmlAttributePanel;
import org.intellij.plugins.intelliLang.inject.config.ui.XmlTagPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.configurables.XmlAttributeInjectionConfigurable;
import org.intellij.plugins.intelliLang.inject.config.ui.configurables.XmlTagInjectionConfigurable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class XmlLanguageInjectorSupport implements LanguageInjectorSupport {

  private static boolean isMine(final PsiLanguageInjectionHost host) {
    if (host instanceof XmlAttributeValue) {
      final PsiElement p = host.getParent();
      if (p instanceof XmlAttribute) {
        final String s = ((XmlAttribute)p).getName();
        return !(s.equals("xmlns") || s.startsWith("xmlns:"));
      }
    }
    else if (host instanceof XmlText) {
      final XmlTag tag = ((XmlText)host).getParentTag();
      return tag != null/* && tag.getValue().getTextElements().length == 1 && tag.getSubTags().length == 0*/;
    }
    return false;
  }

  public boolean addInjectionInPlace(final Language language, final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    if (psiElement instanceof XmlAttributeValue) {
      return doInjectInAttributeValue((XmlAttributeValue)psiElement, language.getID());
    }
    else if (psiElement instanceof XmlText) {
      return doInjectInXmlText((XmlText)psiElement, language.getID());
    }
    return false;
  }

  public boolean removeInjectionInPlace(final PsiLanguageInjectionHost host) {
    if (!isMine(host)) return false;
    final Configuration configuration = Configuration.getInstance();
    final ArrayList<AbstractTagInjection> injections = collectInjections(host, configuration);
    if (injections.isEmpty()) return false;
    addRemoveInjections(host.getProject(), configuration, Collections.<AbstractTagInjection>emptyList(), injections);
    return true;
  }

  private static void addRemoveInjections(final Project project, final Configuration configuration, final List<? extends AbstractTagInjection> newInjections,
                                          final List<? extends AbstractTagInjection> originalInjections) {
    final UndoableAction action = new UndoableAction() {
      public void undo() throws UnexpectedUndoException {
        addRemoveInjectionsInner(project, configuration, originalInjections, newInjections);
      }

      public void redo() throws UnexpectedUndoException {
        addRemoveInjectionsInner(project, configuration, newInjections, originalInjections);
      }

      public DocumentReference[] getAffectedDocuments() {
        return DocumentReference.EMPTY_ARRAY;
      }

      public boolean isComplex() {
        return true;
      }

    };
    new WriteCommandAction.Simple(project) {
      public void run() {
        addRemoveInjectionsInner(project, configuration, newInjections, originalInjections);
        UndoManager.getInstance(project).undoableActionPerformed(action);
      }
    }.execute();
  }

  private static void addRemoveInjectionsInner(final Project project, final Configuration configuration, final List<? extends AbstractTagInjection> newInjections, final List<? extends AbstractTagInjection> originalInjections) {
    configuration.getTagInjections().removeAll(originalInjections);
    configuration.getAttributeInjections().removeAll(originalInjections);
    for (AbstractTagInjection injection : newInjections) {
      if (injection instanceof XmlTagInjection) {
        configuration.getTagInjections().add((XmlTagInjection)injection);
      }
      else if (injection instanceof XmlAttributeInjection) {
        configuration.getAttributeInjections().add((XmlAttributeInjection)injection);
      }
    }
    configuration.configurationModified();
    FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
  }

  public boolean editInjectionInPlace(final PsiLanguageInjectionHost host) {
    if (!isMine(host)) return false;
    final Configuration configuration = Configuration.getInstance();
    final ArrayList<AbstractTagInjection> injections = collectInjections(host, configuration);
    if (injections.isEmpty()) return false;
    final Project project = host.getProject();
    final AbstractTagInjection originalInjection = injections.get(0);
    final AbstractTagInjection newInjection = (AbstractTagInjection)originalInjection.copy();

    final AbstractInjectionPanel panel;
    if (newInjection instanceof XmlTagInjection) {
      panel = new XmlTagPanel((XmlTagInjection)newInjection, project);
    }
    else if (newInjection instanceof XmlAttributeInjection) {
      panel = new XmlAttributePanel((XmlAttributeInjection)newInjection, project);
    }
    else {
      throw new AssertionError(newInjection);
    }
    panel.reset();
    final DialogBuilder builder = new DialogBuilder(project);
    builder.addOkAction();
    builder.addCancelAction();
    builder.setCenterPanel(panel.getComponent());
    builder.setTitle(EditInjectionSettingsAction.EDIT_INJECTION_TITLE);
    builder.setOkOperation(new Runnable() {
      public void run() {
        panel.apply();
        builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
      }
    });
    if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
      addRemoveInjections(project, configuration, Collections.singletonList(newInjection), Collections.singletonList(originalInjection));
    }
    return true;
  }

  private static boolean doInjectInXmlText(final XmlText host, final String languageId) {
    final XmlTag tag = host.getParentTag();
    if (tag != null) {
      final XmlTagInjection injection = new XmlTagInjection();
      injection.setInjectedLanguageId(languageId);
      injection.setTagName(tag.getLocalName());
      injection.setTagNamespace(tag.getNamespace());
      doEditInjection(host.getProject(), injection);
      return true;
    }
    return false;
  }

  private static void doEditInjection(final Project project, final XmlTagInjection template) {
    final Configuration configuration = Configuration.getInstance();
    final XmlTagInjection originalInjection = configuration.findExistingInjection(template);
    final XmlTagInjection newInjection = originalInjection == null? template : originalInjection.copy();
    if (InjectLanguageAction.doEditConfigurable(project, new XmlTagInjectionConfigurable(newInjection, null, project))) {
      addRemoveInjections(project, configuration, Collections.singletonList(newInjection), Collections.singletonList(originalInjection));
    }
  }

  private static boolean doInjectInAttributeValue(final XmlAttributeValue host, final String languageId) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(host, XmlAttribute.class, true);
    final XmlTag tag = attribute == null? null : attribute.getParent();
    if (tag != null) {
      final XmlAttributeInjection injection = new XmlAttributeInjection();
      injection.setInjectedLanguageId(languageId);
      injection.setAttributeName(attribute.getLocalName());
      injection.setAttributeNamespace(attribute.getNamespace());
      injection.setTagName(tag.getLocalName());
      injection.setTagNamespace(tag.getNamespace());
      doEditInjection(host.getProject(), injection);
      return true;
    }
    return false;
  }

  private static void doEditInjection(final Project project, final XmlAttributeInjection template) {
    final Configuration configuration = Configuration.getInstance();
    final XmlAttributeInjection originalInjection = configuration.findExistingInjection(template);
    final XmlAttributeInjection newInjection = originalInjection == null ? template : originalInjection.copy();
    if (InjectLanguageAction.doEditConfigurable(project, new XmlAttributeInjectionConfigurable(newInjection, null, project))) {
      addRemoveInjections(project, configuration, Collections.singletonList(newInjection), Collections.singletonList(originalInjection));
    }
  }

  private static ArrayList<AbstractTagInjection> collectInjections(final PsiLanguageInjectionHost host,
                                        final Configuration configuration) {
    final ArrayList<AbstractTagInjection> injectionsToRemove = new ArrayList<AbstractTagInjection>();
    if (host instanceof XmlAttributeValue) {
      for (final XmlAttributeInjection injection : configuration.getAttributeInjections()) {
        if (injection.isApplicable((XmlAttributeValue)host)) {
          injectionsToRemove.add(injection);
        }
      }
    }
    else if (host instanceof XmlText) {
      final XmlTag tag = ((XmlText)host).getParentTag();
      if (tag != null) {
        for (XmlTagInjection injection : configuration.getTagInjections()) {
          if (injection.isApplicable(tag)) {
            injectionsToRemove.add(injection);
          }
        }
      }
    }
    return injectionsToRemove;
  }

}
