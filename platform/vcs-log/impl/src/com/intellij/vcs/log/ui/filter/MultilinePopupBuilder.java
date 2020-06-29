/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.filter;

import com.google.common.primitives.Chars;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.impl.AutoPopupSupportingListener;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.SoftWrapsEditorCustomization;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.intellij.util.textCompletion.ValuesCompletionProvider.ValuesCompletionProviderDumbAware;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.log.VcsLogBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.Collection;
import java.util.List;

class MultilinePopupBuilder {
  static final char[] SEPARATORS = {'|', '\n'};

  @NotNull private final EditorTextField myTextField;
  private final char[] mySeparators;

  MultilinePopupBuilder(@NotNull Project project,
                        @NotNull Collection<String> values,
                        @NotNull String initialValue,
                        @Nullable CompletionPrefixProvider completionPrefixProvider) {
    myTextField = new TextFieldWithCompletion(project, new MyCompletionProvider(values, completionPrefixProvider),
                                              initialValue, false, true, false);
    setupTextField(myTextField);
    mySeparators = SEPARATORS;
  }

  MultilinePopupBuilder(@NotNull Project project, @NotNull String initialValue, char[] separators) {
    myTextField = new LanguageTextField(PlainTextLanguage.INSTANCE, project, initialValue,
                                        new LanguageTextField.SimpleDocumentCreator(), false);
    setupTextField(myTextField);
    mySeparators = separators;
  }

  @NotNull
  JBPopup createPopup() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(myTextField, BorderLayout.CENTER);
    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, myTextField)
      .setCancelOnClickOutside(true)
      .setAdText(getAdText())
      .setRequestFocus(true)
      .setResizable(true)
      .setMayBeParent(true);

    JBPopup popup = builder.createPopup();
    popup.setMinimumSize(new JBDimension(200, 90));
    AnAction okAction = new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        unregisterCustomShortcutSet(popup.getContent());
        popup.closeOk(e.getInputEvent());
      }
    };
    okAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, popup.getContent());
    AutoPopupSupportingListener.installOn(popup);
    return popup;
  }

  @NotNull
  private String getAdText() {
    return VcsLogBundle.message("vcs.log.filter.popup.advertisement.with.key.text", getSeparatorsText(mySeparators),
                                KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.getShortcuts()));
  }

  @NotNull
  List<String> getSelectedValues() {
    return ContainerUtil.mapNotNull(StringUtil.tokenize(myTextField.getText(), new String(mySeparators)), value -> {
      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
    });
  }

  @NotNull
  private static String getSeparatorsText(char[] separators) {
    StringBuilder s = new StringBuilder();
    for (char c : separators) {
      String separator = c == '\n' ? VcsLogBundle.message("vcs.log.filter.popup.advertisement.text.new.lines") : Character.toString(c);
      if (s.length() == 0) {
        s.append(separator);
      }
      else {
        s.append(VcsLogBundle.message("vcs.log.filter.popup.advertisement.text.or.suffix", separator));
      }
    }
    return s.toString();
  }

  private static void setupTextField(@NotNull EditorTextField textField) {
    textField.addSettingsProvider(editor -> SoftWrapsEditorCustomization.ENABLED.customize(editor));
    textField.setBorder(new CompoundBorder(JBUI.Borders.empty(2), textField.getBorder()));
  }

  interface CompletionPrefixProvider {
    String getPrefix(@NotNull String text, int offset);
  }

  private static class MyCompletionProvider extends ValuesCompletionProviderDumbAware<String> {
    @Nullable private final CompletionPrefixProvider myCompletionPrefixProvider;

    MyCompletionProvider(@NotNull Collection<String> values, @Nullable CompletionPrefixProvider completionPrefixProvider) {
      super(new DefaultTextCompletionValueDescriptor.StringValueDescriptor(), Chars.asList(SEPARATORS), values, false);
      myCompletionPrefixProvider = completionPrefixProvider;
    }

    @Nullable
    @Override
    public String getPrefix(@NotNull String text, int offset) {
      if (myCompletionPrefixProvider != null) {
        return myCompletionPrefixProvider.getPrefix(text, offset);
      }
      return super.getPrefix(text, offset);
    }

    @Nullable
    @Override
    public String getAdvertisement() {
      return VcsLogBundle.message("vcs.log.filter.popup.advertisement.text", getSeparatorsText(SEPARATORS));
    }
  }
}
