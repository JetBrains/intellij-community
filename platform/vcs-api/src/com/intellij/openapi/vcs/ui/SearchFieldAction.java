// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.ui.SearchTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.event.KeyEvent;

/**
 * @deprecated Use {@link SearchTextField}.
 */
@Deprecated
public abstract class SearchFieldAction extends AnAction implements CustomComponentAction {
  private final JPanel myComponent;
  private final SearchTextField myField;

  public SearchFieldAction(String text) {
    super("Find: ");
    myField = new SearchTextField(true) {
      @Override
      protected boolean preprocessEventForTextField(KeyEvent e) {
        if ((KeyEvent.VK_ENTER == e.getKeyCode()) || ('\n' == e.getKeyChar())) {
          e.consume();
          addCurrentTextToHistory();
          perform();
        }
        return super.preprocessEventForTextField(e);
      }

      @Override
      protected void onFocusLost() {
        myField.addCurrentTextToHistory();
        perform();
      }

      @Override
      protected void onFieldCleared() {
        perform();
      }
    };
    Border border = myField.getBorder();
    Border emptyBorder = JBUI.Borders.empty(3, 0, 2, 0);
    if (border instanceof CompoundBorder) {
      if (!UIUtil.isUnderDarcula()) {
        myField.setBorder(new CompoundBorder(emptyBorder, ((CompoundBorder)border).getInsideBorder()));
      }
    }
    else {
      myField.setBorder(emptyBorder);
    }
    myComponent = new JPanel();
    final BoxLayout layout = new BoxLayout(myComponent, BoxLayout.X_AXIS);
    myComponent.setLayout(layout);
    if (text.length() > 0) {
      final JLabel label = new JLabel(text);
      //label.setFont(label.getFont().deriveFont(Font.ITALIC));
      label.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getInactiveTextColor());
      label.setBorder(JBUI.Borders.emptyLeft(3));
      myComponent.add(label);
    }
    myComponent.add(myField);
  }

  private void perform() {
    actionPerformed(AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataId -> null));
  }

  public String getText() {
    return myField.getText();
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation) {
    return myComponent;
  }

  public void setTextFieldFg(boolean inactive) {
    myField.getTextEditor().setForeground(inactive ? UIUtil.getInactiveTextColor() : UIUtil.getActiveTextColor());
  }
}
