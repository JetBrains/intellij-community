/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class InjectLanguageAction implements IntentionAction {
  @NotNull
  public String getText() {
    return "Inject Language";
  }

  @NotNull
  public String getFamilyName() {
    return "Inject Language";
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) {
      return false;
    }
    else {
      final List<Pair<PsiElement, TextRange>> injectedPsi = host.getInjectedPsi();
      if (host instanceof XmlText) {
        final XmlTag tag = ((XmlText)host).getParentTag();
        if (tag == null || tag.getValue().getTextElements().length > 1 || tag.getSubTags().length > 0) {
          return false;
        }
      }
      if (injectedPsi == null || injectedPsi.size() == 0) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiLanguageInjectionHost findInjectionHost(Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiLanguageInjectionHost host =
        PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiLanguageInjectionHost.class, false, true);
    if (host == null) {
      return null;
    }
    if (host instanceof PsiLiteralExpression) {
      final PsiType type = ((PsiLiteralExpression)host).getType();
      if (type == null || !type.equalsToText("java.lang.String")) {
        return null;
      }
    }
    else if (host instanceof XmlAttributeValue) {
      final PsiElement p = host.getParent();
      if (p instanceof XmlAttribute) {
        final String s = ((XmlAttribute)p).getName();
        if (s.equals("xmlns") || s.startsWith("xmlns:")) {
          return null;
        }
      }
    }
    return host;
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    assert host != null;

    final String[] langIds = InjectedLanguage.getAvailableLanguageIDs();
    Arrays.sort(langIds);

    final Map<String, List<String>> map = new LinkedHashMap<String, List<String>>();
    buildLanguageTree(langIds, map);

    final BaseListPopupStep<String> step = new MyPopupStep(map, new ArrayList<String>(map.keySet()), project, host);

    final ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(step);
    listPopup.showInBestPositionFor(DataManager.getInstance().getDataContext());
  }

  private static void buildLanguageTree(String[] langIds, Map<String, List<String>> map) {
    for (final String id : langIds) {
      if (!map.containsKey(id)) {
        map.put(id, new ArrayList<String>());
      }
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

  private static class MyPopupStep extends BaseListPopupStep<String> {
    private final Map<String, List<String>> myMap;
    private final Project myProject;
    private final PsiLanguageInjectionHost myHost;

    public MyPopupStep(Map<String, List<String>> map, List<String> values, Project project, PsiLanguageInjectionHost host) {
      super("Choose Language", values);
      myMap = map;
      myProject = project;
      myHost = host;
    }

    @Override
    public PopupStep onChosen(String selectedValue, boolean finalChoice) {
      if (finalChoice) {
        CustomLanguageInjector.getInstance(myProject).addTempInjection(myHost, InjectedLanguage.create(selectedValue));
        DaemonCodeAnalyzer.getInstance(myProject).restart();

        return FINAL_CHOICE;
      }
      return new MyPopupStep(myMap, myMap.get(selectedValue), myProject, myHost);
    }

    @Override
    public boolean hasSubstep(String selectedValue) {
      return myMap.containsKey(selectedValue) && !myMap.get(selectedValue).isEmpty();
    }

    @Override
    public Icon getIconFor(String aValue) {
      final Language language = InjectedLanguage.findLanguageById(aValue);
      assert language != null;
      final FileType ft = language.getAssociatedFileType();
      return ft != null ? ft.getIcon() : new EmptyIcon(16);
    }

    @NotNull
    @Override
    public String getTextFor(String value) {
      final Language language = InjectedLanguage.findLanguageById(value);
      assert language != null;
      final FileType ft = language.getAssociatedFileType();
      return value + (ft != null ? " ("+ft.getDescription()+")" : "");
    }
  }
}
