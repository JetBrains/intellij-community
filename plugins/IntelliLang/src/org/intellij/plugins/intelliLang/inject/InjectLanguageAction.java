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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.intellij.plugins.intelliLang.Configuration;

import javax.swing.*;
import java.util.*;

public class InjectLanguageAction implements IntentionAction {
  @NonNls protected static final String INJECT_LANGUAGE_FAMILY = "Inject Language";

  @NotNull
  public String getText() {
    return INJECT_LANGUAGE_FAMILY;
  }

  @NotNull
  public String getFamilyName() {
    return INJECT_LANGUAGE_FAMILY;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) return false;
    final List<Pair<PsiElement, TextRange>> injectedPsi = InjectedLanguageUtil.getInjectedPsiFiles(host);
    return injectedPsi == null || injectedPsi.isEmpty();
  }

  @Nullable
  protected static PsiLanguageInjectionHost findInjectionHost(Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    return PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiLanguageInjectionHost.class, false);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    assert host != null;
    doChooseLanguageToInject(new Processor<String>() {
      public boolean process(final String languageId) {
        if (defaultFunctionalityWorked(host, languageId)) return false;
        final Language language = InjectedLanguage.findLanguageById(languageId);
        try {
          for (LanguageInjectionSupport support : Extensions.getExtensions(LanguageInjectionSupport.EP_NAME)) {
            if (support.addInjectionInPlace(language, host)) return false;
          }
          TemporaryPlacesRegistry.getInstance(project).addHostWithUndo(host, InjectedLanguage.create(languageId));
        }
        finally {
          FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
        }
        return false;
      }
    });
  }

  private static boolean defaultFunctionalityWorked(final PsiLanguageInjectionHost host, final String languageId) {
    return Configuration.getInstance().setHostInjectionEnabled(host, Collections.singleton(languageId), true);
  }

  private static boolean doChooseLanguageToInject(final Processor<String> onChosen) {
    final String[] langIds = InjectedLanguage.getAvailableLanguageIDs();
    Arrays.sort(langIds);

    final Map<String, List<String>> map = new LinkedHashMap<String, List<String>>();
    buildLanguageTree(langIds, map);

    final BaseListPopupStep<String> step = new MyPopupStep(map, new ArrayList<String>(map.keySet()), onChosen);

    final ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(step);
    listPopup.showInBestPositionFor(DataManager.getInstance().getDataContext());
    return true;
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

  public static boolean doEditConfigurable(final Project project, final Configurable configurable) {
    return true; //ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
  }

  private static class MyPopupStep extends BaseListPopupStep<String> {
    private final Map<String, List<String>> myMap;
    private final Processor<String> myFinalStepProcessor;

    public MyPopupStep(final Map<String, List<String>> map, final List<String> values, final Processor<String> finalStepProcessor) {
      super("Choose Language", values);
      myMap = map;
      myFinalStepProcessor = finalStepProcessor;
    }

    @Override
    public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
      if (finalChoice) {
        myFinalStepProcessor.process(selectedValue);
        return FINAL_CHOICE;
      }
      return new MyPopupStep(myMap, myMap.get(selectedValue), myFinalStepProcessor);
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
