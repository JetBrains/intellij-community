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
package com.intellij.openapi.application.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.OptionsDialog;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 2, 2005
 */
public class ConfirmExitDialog extends OptionsDialog {

  private final boolean myHasTasks;

  public ConfirmExitDialog(boolean hasBgTasks) {
    super(false);
    setTitle(ApplicationBundle.message("exit.confirm.title"));
    myHasTasks = hasBgTasks;
    init();
  }

  protected Action[] createActions() {
    setOKButtonText(CommonBundle.getYesButtonText());
    setCancelButtonText(CommonBundle.getNoButtonText());
    return new Action[] {getOKAction(), getCancelAction()};
  }

  protected boolean isToBeShown() {
    return GeneralSettings.getInstance().isConfirmExit() || myHasTasks;
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    GeneralSettings.getInstance().setConfirmExit(value);
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return !myHasTasks;
  }

  protected boolean canBeHidden() {
    return !myHasTasks;
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());

    final String message = ApplicationBundle.message(myHasTasks ? "exit.confirm.prompt.tasks": "exit.confirm.prompt", ApplicationNamesInfo.getInstance().getFullProductName());

    final JLabel label = new JLabel(message);
    label.setIconTextGap(10);
    label.setIcon(Messages.getQuestionIcon());
    panel.add(label, BorderLayout.CENTER);
    panel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);
    return panel;
  }
}
