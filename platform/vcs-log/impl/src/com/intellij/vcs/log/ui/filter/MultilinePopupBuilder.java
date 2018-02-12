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
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.SoftWrapsEditorCustomization;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.intellij.util.textCompletion.ValuesCompletionProvider.ValuesCompletionProviderDumbAware;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.Collection;
import java.util.List;

class MultilinePopupBuilder {
  private static final char[] SEPARATORS = {'|', '\n'};

  @NotNull private final EditorTextField myTextField;

  MultilinePopupBuilder(@NotNull Project project,
                        @NotNull final Collection<String> values,
                        @NotNull String initialValue,
                        boolean supportsNegativeValues) {
    myTextField = createTextField(project, values, supportsNegativeValues, initialValue);
  }

  @NotNull
  private static EditorTextField createTextField(@NotNull Project project,
                                                 Collection<String> values,
                                                 boolean supportsNegativeValues,
                                                 @NotNull String initialValue) {
    TextFieldWithCompletion textField =
      new TextFieldWithCompletion(project, new MyCompletionProvider(values, supportsNegativeValues), initialValue, false, true, false) {
        @Override
        protected EditorEx createEditor() {
          EditorEx editor = super.createEditor();
          SoftWrapsEditorCustomization.ENABLED.customize(editor);
          return editor;
        }
      };
    textField.setBorder(new CompoundBorder(JBUI.Borders.empty(2), textField.getBorder()));
    return textField;
  }

  @NotNull
  JBPopup createPopup() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(myTextField, BorderLayout.CENTER);
    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, myTextField)
      .setCancelOnClickOutside(true)
      .setAdText(KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.getShortcuts()) + " to finish")
      .setRequestFocus(true)
      .setResizable(true)
      .setMayBeParent(true);

    final JBPopup popup = builder.createPopup();
    popup.setMinimumSize(new JBDimension(200, 90));
    AnAction okAction = new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        unregisterCustomShortcutSet(popup.getContent());
        popup.closeOk(e.getInputEvent());
      }
    };
    okAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, popup.getContent());
    return popup;
  }

  @NotNull
  List<String> getSelectedValues() {
    return ContainerUtil.mapNotNull(StringUtil.tokenize(myTextField.getText(), new String(SEPARATORS)), value -> {
      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
    });
  }

  private static class MyCompletionProvider extends ValuesCompletionProviderDumbAware<String> {
    private final boolean mySupportsNegativeValues;

    MyCompletionProvider(@NotNull Collection<String> values, boolean supportsNegativeValues) {
      super(new DefaultTextCompletionValueDescriptor.StringValueDescriptor(), Chars.asList(SEPARATORS), values, false);
      mySupportsNegativeValues = supportsNegativeValues;
    }

    @Nullable
    @Override
    public String getPrefix(@NotNull String text, int offset) {
      String prefix = super.getPrefix(text, offset);
      if (mySupportsNegativeValues && prefix != null) return StringUtil.trimLeading(prefix, '-');
      return prefix;
    }

    @Nullable
    @Override
    public String getAdvertisement() {
      return "Select one or more values separated with | or new lines";
    }
  }
}
