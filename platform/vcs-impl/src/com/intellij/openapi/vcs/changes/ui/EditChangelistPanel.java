package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListEditHandler;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.ui.DocumentAdapter;
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
  private JTextField myNameTextField;
  private JTextArea myDescriptionTextArea;
  private JPanel myContent;
  private JPanel myAdditionalControlsPanel;
  private JCheckBox myMakeActiveCheckBox;

  @Nullable
  private final ChangeListEditHandler myHandler;
  private Consumer<LocalChangeList> myConsumer;

  public EditChangelistPanel(@Nullable final ChangeListEditHandler handler) {
    myHandler = handler;

    if (myHandler != null) {
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
    myNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String name = getName();
        if ((initial == null || !name.equals(initial.getName())) && ChangeListManager.getInstance(project).findChangeList(name) != null) {
          nameChanged(VcsBundle.message("new.changelist.duplicate.name.error"));
        } else {
          nameChanged(null);
        }
      }
    });
  }

  public void changelistCreatedOrChanged(LocalChangeList list) {
    if (myConsumer != null) {
      myConsumer.consume(list);
    }
  }

  private void onEditComment(ChangeListEditHandler handler) {
    myNameTextField.setText(handler.changeNameOnChangeComment(myNameTextField.getText(), myDescriptionTextArea.getText()));
  }

  private void onEditName(ChangeListEditHandler handler) {
    myDescriptionTextArea.setText(handler.changeCommentOnChangeName(myNameTextField.getText(), myDescriptionTextArea.getText()));
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
