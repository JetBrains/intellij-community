// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.SearchTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.event.KeyEvent;

/**
 * @deprecated Use {@link SearchTextField}.
 */
@Deprecated(forRemoval = true)
public abstract class SearchFieldAction extends AnAction implements CustomComponentAction {
  private final JPanel myComponent;
  private final SearchTextField myField;

  public SearchFieldAction(@Nls String text) {
    super(VcsBundle.messagePointer("action.SearchFieldAction.text.find"));
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
      if (!StartupUiUtil.isUnderDarcula()) {
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
      label.setForeground(StartupUiUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getInactiveTextColor());
      label.setBorder(JBUI.Borders.emptyLeft(3));
      myComponent.add(label);
    }
    myComponent.add(myField);
  }

  private void perform() {
    actionPerformed(ActionUtil.createEmptyEvent());
  }

  public String getText() {
    return myField.getText();
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return myComponent;
  }

  public void setTextFieldFg(boolean inactive) {
    myField.getTextEditor().setForeground(inactive ? UIUtil.getInactiveTextColor() : UIUtil.getActiveTextColor());
  }
}
