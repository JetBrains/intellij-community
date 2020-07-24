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
package com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.OptionsDialog;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public class EditOptionsDialog extends OptionsDialog {

  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final JCheckBox myReservedEdit = new JCheckBox(CvsBundle.message("checkbox.text.reserved.edit"));

  public EditOptionsDialog(Project project) {
    super(project);
    myPanel.add(myReservedEdit, BorderLayout.NORTH);
    myReservedEdit.setSelected(CvsConfiguration.getInstance(project).RESERVED_EDIT);
    setTitle(CvsBundle.message("dialog.title.edit.options"));
    init();
  }

  @Override
  protected boolean isToBeShown() {
    return CvsVcs2.getInstance(myProject).getEditOptions().getValue();
  }

  @Override
  protected void setToBeShown(boolean value, boolean onOk) {
    CvsVcs2.getInstance(myProject).getEditOptions().setValue(value);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    CvsConfiguration.getInstance(myProject).RESERVED_EDIT = myReservedEdit.isSelected();
    super.doOKAction();
  }

  @Override
  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }
}
