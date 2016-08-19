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
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.injection.Injectable;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.FileContentUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.references.InjectedReferencesContributor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InjectLanguageAction implements IntentionAction, LowPriorityAction {
  @NonNls private static final String INJECT_LANGUAGE_FAMILY = "Inject language or reference";
  public static final String LAST_INJECTED_LANGUAGE = "LAST_INJECTED_LANGUAGE";
  public static final Key<Processor<PsiLanguageInjectionHost>> FIX_KEY = Key.create("inject fix key");

  public static List<Injectable> getAllInjectables() {
    Language[] languages = InjectedLanguage.getAvailableLanguages();
    List<Injectable> list = new ArrayList<>();
    for (Language language : languages) {
      list.add(Injectable.fromLanguage(language));
    }
    list.addAll(Arrays.asList(ReferenceInjector.EXTENSION_POINT_NAME.getExtensions()));
    Collections.sort(list);
    return list;
  }

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
    final List<Pair<PsiElement, TextRange>> injectedPsi = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host);
    if (injectedPsi == null || injectedPsi.isEmpty()) {
      return !InjectedReferencesContributor.isInjected(file.findReferenceAt(editor.getCaretModel().getOffset()));
    }
    return false;
  }

  @Nullable
  protected static PsiLanguageInjectionHost findInjectionHost(Editor editor, PsiFile file) {
    if (editor instanceof EditorWindow) return null;
    final int offset = editor.getCaretModel().getOffset();
    final PsiLanguageInjectionHost host = PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiLanguageInjectionHost.class, false);
    if (host == null) return null;
    return host.isValidHost()? host : null;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    doChooseLanguageToInject(editor, injectable -> {
      ApplicationManager.getApplication().runReadAction(() -> {
        if (!project.isDisposed()) {
          invokeImpl(project, editor, file, injectable);
        }
      });
      return false;
    });
  }

  public static void invokeImpl(Project project, Editor editor, final PsiFile file, Injectable injectable) {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) return;
    if (defaultFunctionalityWorked(host, injectable.getId())) return;

    try {
      host.putUserData(FIX_KEY, null);
      Language language = injectable.toLanguage();
      for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
        if (support.isApplicableTo(host) && support.addInjectionInPlace(language, host)) {
          return;
        }
      }
      if (TemporaryPlacesRegistry.getInstance(project).getLanguageInjectionSupport().addInjectionInPlace(language, host)) {
        final Processor<PsiLanguageInjectionHost> data = host.getUserData(FIX_KEY);
        String text = StringUtil.escapeXml(language.getDisplayName()) + " was temporarily injected.";
        if (data != null) {
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            final SmartPsiElementPointer<PsiLanguageInjectionHost> pointer =
              SmartPointerManager.getInstance(project).createSmartPsiElementPointer(host);
            final TextRange range = host.getTextRange();
            HintManager.getInstance().showQuestionHint(editor, text + "<br>Do you want to insert annotation? " + KeymapUtil
              .getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)),
                                                       range.getStartOffset(), range.getEndOffset(), new QuestionAction() {
              @Override
              public boolean execute() {
                return data.process(pointer.getElement());
              }
            });
          }
        }
        else {
          HintManager.getInstance().showInformationHint(editor, text);
        }
      }
    }
    finally {
      if (injectable.getLanguage() != null) {    // no need for reference injection
        FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
      }
      else {
        ((PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker()).incCounter();
        DaemonCodeAnalyzer.getInstance(project).restart();
      }
    }
  }

  private static boolean defaultFunctionalityWorked(final PsiLanguageInjectionHost host, String id) {
    return Configuration.getProjectInstance(host.getProject()).setHostInjectionEnabled(host, Collections.singleton(id), true);
  }

  public static boolean doChooseLanguageToInject(Editor editor, final Processor<Injectable> onChosen) {
    final List<Injectable> injectables = getAllInjectables();

    final JList list = new JBList(injectables);
    list.setCellRenderer(new ColoredListCellRendererWrapper<Injectable>() {
      @Override
      protected void doCustomize(JList list, Injectable language, int index, boolean selected, boolean hasFocus) {
        setIcon(language.getIcon());
        append(language.getDisplayName());
        String description = language.getAdditionalDescription();
        if (description != null) {
          append(description, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      }
    });
    Dimension minSize = new JLabel(PlainTextLanguage.INSTANCE.getDisplayName(), EmptyIcon.ICON_16, SwingConstants.LEFT).getMinimumSize();
    minSize.height *= 4;
    list.setMinimumSize(minSize);
    JBPopup popup = new PopupChooserBuilder(list).setItemChoosenCallback(() -> {
      Injectable value = (Injectable)list.getSelectedValue();
      if (value != null) {
        onChosen.process(value);
        PropertiesComponent.getInstance().setValue(LAST_INJECTED_LANGUAGE, value.getId());
      }
    }).setFilteringEnabled(language -> ((Injectable)language).getDisplayName()).setMinSize(minSize).createPopup();
    final String lastInjected = PropertiesComponent.getInstance().getValue(LAST_INJECTED_LANGUAGE);
    if (lastInjected != null) {
      Injectable injectable = ContainerUtil.find(injectables, injectable1 -> lastInjected.equals(injectable1.getId()));
      list.setSelectedValue(injectable, true);
    }
    popup.showInBestPositionFor(editor);
    return true;
  }

  public boolean startInWriteAction() {
    return false;
  }

}
