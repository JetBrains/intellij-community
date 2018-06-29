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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.injection.Injectable;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.FileContentUtil;
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

  private static final FixPresenter DEFAULT_FIX_PRESENTER = (editor, range, pointer, text, handler) -> {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    HintManager.getInstance().showQuestionHint(editor, text, range.getStartOffset(), range.getEndOffset(), new QuestionAction() {
      @Override
      public boolean execute() {
        return handler.process(pointer.getElement());
      }
    });
  };

  @NotNull
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

  public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) return false;
    List<Pair<PsiElement, TextRange>> injectedPsi = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host);
    if (injectedPsi == null || injectedPsi.isEmpty()) {
      return !InjectedReferencesContributor.isInjected(file.findReferenceAt(editor.getCaretModel().getOffset()));
    }
    return false;
  }

  @Nullable
  protected static PsiLanguageInjectionHost findInjectionHost(@NotNull Editor editor,
                                                              @NotNull PsiFile file) {
    if (editor instanceof EditorWindow) return null;
    int offset = editor.getCaretModel().getOffset();
    FileViewProvider vp = file.getViewProvider();
    for (Language language : vp.getLanguages()) {
      PsiLanguageInjectionHost host = PsiTreeUtil.getParentOfType(vp.findElementAt(offset, language), PsiLanguageInjectionHost.class, false);
      if (host != null && host.isValidHost()) {
        return host;
      }
    }
    return null;
  }

  public void invoke(@NotNull Project project,
                     @NotNull Editor editor,
                     @NotNull PsiFile file) throws IncorrectOperationException {
    SmartPsiElementPointer<PsiFile> filePointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(file);
    doChooseLanguageToInject(editor, injectable -> {
      ReadAction.run(() -> {
        if (project.isDisposed()) return;
        PsiFile psiFile = filePointer.getElement();
        if (psiFile == null || editor.isDisposed()) return;
        invokeImpl(project, editor, psiFile, injectable);
      });
      return false;
    });
  }

  public static void invokeImpl(@NotNull Project project,
                                @NotNull Editor editor,
                                @NotNull PsiFile file,
                                @NotNull Injectable injectable) {
    invokeImpl(project, editor, file, injectable, DEFAULT_FIX_PRESENTER);
  }

  public static void invokeImpl(@NotNull Project project,
                                @NotNull Editor editor,
                                @NotNull PsiFile file,
                                @NotNull Injectable injectable,
                                @NotNull FixPresenter fixPresenter) {
    PsiLanguageInjectionHost host = findInjectionHost(editor, file);
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
        Processor<PsiLanguageInjectionHost> fixer = host.getUserData(FIX_KEY);
        String text = StringUtil.escapeXml(language.getDisplayName()) + " was temporarily injected.";
        if (fixer != null) {
          SmartPsiElementPointer<PsiLanguageInjectionHost> pointer =
            SmartPointerManager.getInstance(project).createSmartPsiElementPointer(host);
          String fixText = text + "<br>Do you want to insert annotation? " + KeymapUtil
            .getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
          fixPresenter.showFix(editor, host.getTextRange(), pointer, fixText, host1 -> {
            List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host1);
            if (files != null) {
              for (Pair<PsiElement, TextRange> pair: files) {
                PsiFile psiFile = (PsiFile)pair.first;
                LanguageInjectionSupport languageInjectionSupport = psiFile.getUserData(LanguageInjectionSupport.INJECTOR_SUPPORT);
                if (languageInjectionSupport != null) {
                  languageInjectionSupport.removeInjectionInPlace(host1);
                }
              }
            }
            else {
              LanguageInjectionSupport support = host1.getUserData(LanguageInjectionSupport.INJECTOR_SUPPORT);
              if (support != null) {
                if (support.removeInjection(host)) {
                  host1.getManager().dropPsiCaches();
                }
              }
            }

            return fixer.process(host1);
          });
        }
        else {
          HintManager.getInstance().showInformationHint(editor, text);
        }
      }
    }
    finally {
      if (injectable.getLanguage() != null) {    // no need for reference injection
        FileContentUtil.reparseFiles(project, Collections.emptyList(), true);
      }
      else {
        PsiManager.getInstance(project).dropPsiCaches();
      }
    }
  }

  private static boolean defaultFunctionalityWorked(PsiLanguageInjectionHost host, String id) {
    return Configuration.getProjectInstance(host.getProject()).setHostInjectionEnabled(host, Collections.singleton(id), true);
  }

  public static boolean doChooseLanguageToInject(Editor editor, final Processor<Injectable> onChosen) {
    ColoredListCellRenderer<Injectable> renderer = new ColoredListCellRenderer<Injectable>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends Injectable> list,
                                           Injectable language,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        setIcon(language.getIcon());
        append(language.getDisplayName());
        String description = language.getAdditionalDescription();
        if (description != null) {
          append(description, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      }
    };

    final List<Injectable> injectables = getAllInjectables();

    final String lastInjectedId = PropertiesComponent.getInstance().getValue(LAST_INJECTED_LANGUAGE);
    Injectable lastInjected = lastInjectedId != null ? ContainerUtil.find(injectables, injectable -> lastInjectedId.equals(injectable.getId())) : null;

    Dimension minSize = new JLabel(PlainTextLanguage.INSTANCE.getDisplayName(), EmptyIcon.ICON_16, SwingConstants.LEFT).getMinimumSize();
    minSize.height *= 4;

    IPopupChooserBuilder<Injectable> builder = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(injectables)
      .setRenderer(renderer)
      .setItemChosenCallback(injectable -> {
        onChosen.process(injectable);
        PropertiesComponent.getInstance().setValue(LAST_INJECTED_LANGUAGE, injectable.getId());
      })
      .setMinSize(minSize)
      .setNamerForFiltering(language -> language.getDisplayName());
    if (lastInjected != null) {
      builder = builder.setSelectedValue(lastInjected, true);
    }
    builder.createPopup().showInBestPositionFor(editor);
    return true;
  }

  public boolean startInWriteAction() {
    return false;
  }

  public interface FixPresenter {
    void showFix(@NotNull Editor editor,
                 @NotNull TextRange range,
                 @NotNull SmartPsiElementPointer<PsiLanguageInjectionHost> pointer,
                 @NotNull String text,
                 @NotNull Processor<PsiLanguageInjectionHost> data);
  }
}
