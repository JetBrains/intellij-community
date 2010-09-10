/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListEditHandler;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorTextField;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * @author max
 */
public abstract class EditChangelistPanel {
  private EditorTextField myNameTextField;
  private EditorTextField myDescriptionTextArea;
  private JPanel myContent;
  private JPanel myAdditionalControlsPanel;
  private JCheckBox myMakeActiveCheckBox;

  @Nullable
  private final ChangeListEditHandler myHandler;
  private Consumer<LocalChangeList> myConsumer;

  public EditChangelistPanel(@Nullable final ChangeListEditHandler handler) {
    myHandler = handler;

    myNameTextField.addKeyListener(new KeyListener() {
      public void keyTyped(final KeyEvent e) {
        onEditName(EditChangelistPanel.this.myHandler);
      }
      public void keyPressed(final KeyEvent e) {
      }
      public void keyReleased(final KeyEvent e) {
        onEditName(EditChangelistPanel.this.myHandler);
      }
    });
    myNameTextField.addInputMethodListener(new InputMethodListener() {
      public void inputMethodTextChanged(final InputMethodEvent event) {
        onEditName(EditChangelistPanel.this.myHandler);
      }
      public void caretPositionChanged(final InputMethodEvent event) {
      }
    });
    if (myHandler != null) {
      myDescriptionTextArea.addKeyListener(new KeyListener() {
        public void keyTyped(final KeyEvent e) {
        }
        public void keyPressed(final KeyEvent e) {
        }
        public void keyReleased(final KeyEvent e) {
          onEditComment(EditChangelistPanel.this.myHandler);
        }
      });
      myDescriptionTextArea.addInputMethodListener(new InputMethodListener() {
        public void inputMethodTextChanged(final InputMethodEvent event) {
          onEditComment(EditChangelistPanel.this.myHandler);
        }
        public void caretPositionChanged(final InputMethodEvent event) {
        }
      });
    }
  }

  public JCheckBox getMakeActiveCheckBox() {
    return myMakeActiveCheckBox;
  }

  public void init(final Project project, final LocalChangeList initial) {
    myMakeActiveCheckBox.setSelected(VcsConfiguration.getInstance(project).MAKE_NEW_CHANGELIST_ACTIVE);
    for (EditChangelistSupport support : Extensions.getExtensions(EditChangelistSupport.EP_NAME, project)) {
      support.installSearch(myNameTextField, myDescriptionTextArea);
      myConsumer = support.addControls(myAdditionalControlsPanel, initial);
    }
    myNameTextField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(com.intellij.openapi.editor.event.DocumentEvent event) {
      }

      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
        nameChangedImpl(project, initial);
      }
    });
    nameChangedImpl(project, initial);
  }

  protected void nameChangedImpl(final Project project, final LocalChangeList initial) {
    String name = getName();
    if (name == null || name.trim().length() == 0) {
      nameChanged("Cannot create new changelist with empty name.");
    } else if ((initial == null || !name.equals(initial.getName())) && ChangeListManager.getInstance(project).findChangeList(name) != null) {
      nameChanged(VcsBundle.message("new.changelist.duplicate.name.error"));
    } else {
      nameChanged(null);
    }
  }

  public void changelistCreatedOrChanged(LocalChangeList list) {
    if (myConsumer != null) {
      myConsumer.consume(list);
    }
  }

  private void onEditComment(ChangeListEditHandler handler) {
    if (handler != null) {
      myNameTextField.setText(handler.changeNameOnChangeComment(myNameTextField.getText(), myDescriptionTextArea.getText()));
    }
  }

  private void onEditName(ChangeListEditHandler handler) {
    if (handler != null) {
      myDescriptionTextArea.setText(handler.changeCommentOnChangeName(myNameTextField.getText(), myDescriptionTextArea.getText()));
    }
  }

  public void setName(String s) {
    myNameTextField.setText(s);
  }

  public String getName() {
    return myNameTextField.getText();
  }

  public void setDescription(String s) {
    myDescriptionTextArea.setText(s);
  }

  public String getDescription() {
    return myDescriptionTextArea.getText();
  }

  public JComponent getContent() {
    return myContent;
  }

  public void setEnabled(boolean b) {
    UIUtil.setEnabled(myContent, b, true);
  }

  public void requestFocus() {
    myNameTextField.requestFocus();
  }

  public JComponent getPrefferedFocusedComponent() {
    return myNameTextField;
  }

  protected abstract void nameChanged(String errorMessage);
}
