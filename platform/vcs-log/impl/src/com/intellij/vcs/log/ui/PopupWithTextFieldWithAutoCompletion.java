/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.spellchecker.ui.SpellCheckingEditorCustomization;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Collection;

public class PopupWithTextFieldWithAutoCompletion extends TextFieldWithAutoCompletion<String> {

  @Nullable private JBPopup myPopup;

  public PopupWithTextFieldWithAutoCompletion(@NotNull Project project, @NotNull Collection<String> variants) {
    super(project, new StringsCompletionProvider(variants, null), false, null);
    setBorder(new EmptyBorder(3, 3, 3, 3));
  }

  public JBPopup createPopup() {
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, this)
      .setCancelOnClickOutside(true)
      .setCancelOnWindowDeactivation(true)
      .setCancelKeyEnabled(true)
      .setRequestFocus(true)
      .createPopup();

    final JBTextField field = new JBTextField(20);
    final Dimension size = field.getPreferredSize();
    final Insets insets = getBorder().getBorderInsets(this);
    size.height+=6 + insets.top + insets.bottom;
    size.width +=4 + insets.left + insets.right;
    myPopup.setSize(size);

    return myPopup;
  }

  @Override
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
      if (myPopup != null) {
        myPopup.closeOk(e);
      }
      return true;
    }
    else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
      if (myPopup != null) {
        myPopup.cancel(e);
      }
      return true;
    }
    return false;
  }

  @Override
  protected EditorEx createEditor() {
    // spell check is not needed
    EditorEx editor = super.createEditor();
    SpellCheckingEditorCustomization.getInstance(false).customize(editor);
    return editor;
  }
}
