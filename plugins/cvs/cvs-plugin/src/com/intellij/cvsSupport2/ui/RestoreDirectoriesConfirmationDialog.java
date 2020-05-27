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
package com.intellij.cvsSupport2.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * author: lesya
 */
public class RestoreDirectoriesConfirmationDialog extends OptionsDialog{
  private JLabel myIconLabel;
  private JPanel myPanel;

  public RestoreDirectoriesConfirmationDialog() {
    super(null);
    setTitle(CvsBundle.message("dialog.title.restore.directories.information"));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    myIconLabel.setText("");
    myIconLabel.setIcon(Messages.getInformationIcon());
    return myPanel;
  }


  @Override
  protected void setToBeShown(boolean value, boolean onOk) {
    CvsApplicationLevelConfiguration.getInstance().SHOW_RESTORE_DIRECTORIES_CONFIRMATION = value;
  }

  @Override
  protected boolean isToBeShown() {
    return CvsApplicationLevelConfiguration.getInstance().SHOW_RESTORE_DIRECTORIES_CONFIRMATION;
  }

  @Override
  protected Action @NotNull [] createActions() {
    Action okAction = getOKAction();
    UIUtil.setActionNameAndMnemonic(CvsBundle.message("button.text.close"), okAction);
    return new Action[]{okAction};
  }

  @Override
  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }
}
