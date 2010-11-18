/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.ui.SearchTextField;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * @author irengrig
 */
public abstract class SearchFieldAction extends AnAction implements CustomComponentAction {
  private final SearchTextField myField;

  public SearchFieldAction() {
    super("Find: ");
    myField = new SearchTextField(true) {
      @Override
      protected boolean preprocessEventForTextField(KeyEvent e) {
        if ((KeyEvent.VK_ENTER == e.getKeyCode()) || ('\n' == e.getKeyChar())) {
          e.consume();
          addCurrentTextToHistory();
          actionPerformed(null);
        }
        return super.preprocessEventForTextField(e);
      }

      @Override
      protected void onFocusLost() {
        myField.addCurrentTextToHistory();
        actionPerformed(null);
      }
    };
  }

  public String getText() {
    return myField.getText();
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return myField;
  }
}
