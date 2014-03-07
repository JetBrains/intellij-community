/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.spellchecker.ui.SpellCheckingEditorCustomization;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldProvider;
import com.intellij.ui.SoftWrapsEditorCustomization;
import com.intellij.util.TextFieldCompletionProviderDumbAware;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

class MultilinePopupBuilder {

  private static final char[] SEPARATORS = { ',', '|', '\n' };

  @NotNull private final EditorTextField myTextField;

  MultilinePopupBuilder(@NotNull Project project, @NotNull final Collection<String> values) {
    myTextField = createTextField(project);
    new MyCompletionProvider(values).apply(myTextField);
  }

  @NotNull
  private static EditorTextField createTextField(@NotNull Project project) {
    final EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
    List<EditorCustomization>
      features = Arrays.<EditorCustomization>asList(SoftWrapsEditorCustomization.ENABLED, SpellCheckingEditorCustomization.DISABLED);
    EditorTextField textField = service.getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, features);
    textField.setBorder(new CompoundBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2), textField.getBorder()));
    textField.setOneLineMode(false);
    return textField;
  }

  @NotNull
  JBPopup createPopup() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(myTextField, BorderLayout.CENTER);
    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, myTextField)
      .setCancelOnClickOutside(true)
      .setAdText(KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.getShortcuts()) + " to finish")
      .setMovable(true)
      .setRequestFocus(true)
      .setResizable(true)
      .setMayBeParent(true);

    final JBPopup popup = builder.createPopup();
    popup.setMinimumSize(new Dimension(200, 90));
    AnAction okAction = new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        unregisterCustomShortcutSet(popup.getContent());
        popup.closeOk(e.getInputEvent());
      }
    };
    okAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, popup.getContent());
    return popup;
  }

  @NotNull
  Collection<String> getSelectedValues() {
    return ContainerUtil.toCollection(StringUtil.tokenize(myTextField.getText().trim(), new String(SEPARATORS)));
  }

  private static class MyCompletionProvider extends TextFieldCompletionProviderDumbAware {

    @NotNull private final Collection<String> myValues;

    MyCompletionProvider(@NotNull Collection<String> values) {
      super(true);
      myValues = values;
    }

    @NotNull
    @Override
    protected String getPrefix(@NotNull String currentTextPrefix) {
      final int separatorPosition = lastSeparatorPosition(currentTextPrefix);
      return separatorPosition == -1 ? currentTextPrefix : currentTextPrefix.substring(separatorPosition + 1).trim();
    }

    private static int lastSeparatorPosition(@NotNull String text) {
      int lastPosition = -1;
      for (char separator : SEPARATORS) {
        int lio = text.lastIndexOf(separator);
        if (lio > lastPosition) {
          lastPosition = lio;
        }
      }
      return lastPosition;
    }

    @Override
    protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix,
                                         @NotNull CompletionResultSet result) {
      result.addLookupAdvertisement("Select one or more users separated with comma, | or new lines");
      for (String completionVariant : myValues) {
        final LookupElementBuilder element = LookupElementBuilder.create(completionVariant);
        result.addElement(element.withLookupString(completionVariant.toLowerCase()));
      }
    }
  }
}
