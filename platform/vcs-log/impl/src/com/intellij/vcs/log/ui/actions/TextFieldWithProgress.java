/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public abstract class TextFieldWithProgress extends JPanel {
  @NotNull private final TextFieldWithCompletion myTextField;
  @NotNull private final AsyncProcessIcon myProgressIcon;

  public TextFieldWithProgress(@NotNull Project project,
                               @NotNull TextCompletionProvider completionProvider) {
    super(new BorderLayout());
    setBorder(JBUI.Borders.empty(3));

    myProgressIcon = new AsyncProcessIcon("Loading commits");
    myTextField = new TextFieldWithCompletion(project, completionProvider, "", true, true, false) {
      @Override
      public void setBackground(Color bg) {
        super.setBackground(bg);
        myProgressIcon.setBackground(bg);
      }

      @Override
      protected boolean processKeyBinding(KeyStroke ks, final KeyEvent e, int condition, boolean pressed) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          onOk();
          return true;
        }
        return false;
      }
    };
    myTextField.setBorder(IdeBorderFactory.createEmptyBorder());

    myProgressIcon.setOpaque(true);
    myProgressIcon.setBackground(myTextField.getBackground());

    add(myTextField, BorderLayout.CENTER);
    add(myProgressIcon, BorderLayout.EAST);

    hideProgress();
  }

  public JComponent getPreferableFocusComponent() {
    return myTextField;
  }

  public void showProgress() {
    myTextField.setEnabled(false);
    myProgressIcon.setVisible(true);
  }

  public void hideProgress() {
    myTextField.setEnabled(true);
    myProgressIcon.setVisible(false);
  }

  public String getText() {
    return myTextField.getText();
  }

  public abstract void onOk();
}
