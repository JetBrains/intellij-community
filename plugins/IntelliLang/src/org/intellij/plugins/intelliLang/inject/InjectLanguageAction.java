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
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.ui.EmptyIcon;
import org.intellij.plugins.intelliLang.Configuration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InjectLanguageAction implements IntentionAction {
  @NonNls private static final String INJECT_LANGUAGE_FAMILY = "Inject Language";

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
    if (editor instanceof EditorWindow) return null;
    final int offset = editor.getCaretModel().getOffset();
    final PsiLanguageInjectionHost host = PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiLanguageInjectionHost.class, false);
    if (host == null) return null;
    final ElementManipulator<PsiLanguageInjectionHost> manipulator = ElementManipulators.getManipulator(host);
    if (manipulator == null) return null;
    if (file.getFileType() != StdFileTypes.XML) { // allow injection in XmlText
      final TextRange textRange = manipulator.getRangeInElement(host);
      if (textRange.getStartOffset() == 0) return null;
    }
    return host;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    doChooseLanguageToInject(new Processor<String>() {
      public boolean process(final String languageId) {
        if (project.isDisposed()) return false;
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            invokeImpl(project, editor, file, languageId);
          }
        });
        return false;
      }
    });
  }

  private static void invokeImpl(Project project, Editor editor, PsiFile file, String languageId) {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) return;
    if (defaultFunctionalityWorked(host, languageId)) return;
    final Language language = InjectedLanguage.findLanguageById(languageId);
    try {
      for (LanguageInjectionSupport support : Extensions.getExtensions(LanguageInjectionSupport.EP_NAME)) {
        if (support.addInjectionInPlace(language, host)) return;
      }
      TemporaryPlacesRegistry.getInstance(project).addHostWithUndo(host, InjectedLanguage.create(languageId));
    }
    finally {
      FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
    }
  }

  private static boolean defaultFunctionalityWorked(final PsiLanguageInjectionHost host, final String languageId) {
    return Configuration.getInstance().setHostInjectionEnabled(host, Collections.singleton(languageId), true);
  }

  private static boolean doChooseLanguageToInject(final Processor<String> onChosen) {
    final String[] langIds = InjectedLanguage.getAvailableLanguageIDs();
    Arrays.sort(langIds);

    final JList list = new JList(langIds);
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final String aValue = (String)value;
        final Language language = InjectedLanguage.findLanguageById(aValue);
        assert language != null;
        final FileType ft = language.getAssociatedFileType();
        setIcon(ft != null ? ft.getIcon() : new EmptyIcon(16));
        setText(value + (ft != null ? " (" + ft.getDescription() + ")" : ""));
        return this;
      }
    });
    new PopupChooserBuilder(list).setItemChoosenCallback(new Runnable() {
      public void run() {
        final String string = (String)list.getSelectedValue();
        onChosen.process(string);
      }
    }).setFilteringEnabled(new Function<Object, String>() {
      public String fun(Object o) {
        return (String)o;
      }
    }).createPopup().showInBestPositionFor(DataManager.getInstance().getDataContext());
    return true;
  }

  public boolean startInWriteAction() {
    return false;
  }

  public static boolean doEditConfigurable(final Project project, final Configurable configurable) {
    return true; //ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
  }
}
