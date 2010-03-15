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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.util.ui.OptionsDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * @author Dmitry Avdeev
 */
public class VcsConfirmationDialog extends OptionsDialog {

  private final VcsShowConfirmationOption myOption;
  private final String myMessage;
  private final String myDoNotShowMessage;

  protected VcsConfirmationDialog(Project project, VcsShowConfirmationOption option, String message, String doNotShowMessage) {
    super(project);
    myOption = option;
    myMessage = message;
    myDoNotShowMessage = doNotShowMessage;

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
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel(myMessage));
    panel.add(new JLabel(Messages.getQuestionIcon()));
    return panel;
  }

  @Override
  protected String getDoNotShowMessage() {
    return myDoNotShowMessage;
  }

  @Override
  protected Action[] createActions() {
    return new Action[] {
      new AbstractAction("Yes") {
        public void actionPerformed(ActionEvent e) {
          doOKAction();
        }
    }, new AbstractAction("No") {
        public void actionPerformed(ActionEvent e) {
          doCancelAction();
        }
      }};
  }
}
