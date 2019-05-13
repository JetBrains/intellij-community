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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.util.ui.OptionsDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * @author Dmitry Avdeev
 */
class VcsConfirmationDialog extends OptionsDialog {
  @NotNull private final String myOkText;
  @NotNull private final String myCancelText;
  private final VcsShowConfirmationOption myOption;
  private final String myMessage;
  private final String myDoNotShowMessage;

  VcsConfirmationDialog(@NotNull Project project,
                        @NotNull String title,
                        @NotNull String okText,
                        @NotNull String cancelText,
                        @NotNull VcsShowConfirmationOption option,
                        @NotNull String message,
                        @NotNull String doNotShowMessage) {
    super(project);
    myOkText = okText;
    myCancelText = cancelText;
    myOption = option;
    myMessage = message;
    myDoNotShowMessage = doNotShowMessage;
    setTitle(title);
    init();
  }

  @Override
  protected boolean isToBeShown() {
    return myOption.getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
  }

  @Override
  protected void setToBeShown(boolean value, boolean onOk) {
    myOption.setValue(value ? VcsShowConfirmationOption.Value.SHOW_CONFIRMATION : onOk ? VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY : VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY);
  }

  @Override
  protected boolean shouldSaveOptionsOnCancel() {
    return true;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout(15, 0));
    panel.add(new JLabel(myMessage));
    panel.add(new JLabel(Messages.getQuestionIcon()), BorderLayout.WEST);
    return panel;
  }

  @NotNull
  @Override
  protected String getDoNotShowMessage() {
    return myDoNotShowMessage;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    final AbstractAction okAction = new AbstractAction(myOkText) {
      {
        putValue(DEFAULT_ACTION, Boolean.TRUE);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        doOKAction();
      }
    };
    final AbstractAction cancelAction = new AbstractAction(myCancelText) {
      @Override
      public void actionPerformed(ActionEvent e) {
        doCancelAction();
      }
    };
    return SystemInfo.isMac ? new Action[] {cancelAction, okAction} : new Action[] {okAction, cancelAction};
  }
}
