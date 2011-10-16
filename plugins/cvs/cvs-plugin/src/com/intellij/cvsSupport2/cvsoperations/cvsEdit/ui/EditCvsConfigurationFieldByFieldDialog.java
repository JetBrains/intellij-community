/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.cvsSupport2.config.ui.CvsRootFieldByFieldConfigurationPanel;
import com.intellij.cvsSupport2.connections.CvsRootDataBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputException;

import javax.swing.*;

/**
 * author: lesya
 */
public class EditCvsConfigurationFieldByFieldDialog extends DialogWrapper {
  private String myConfiguration;
  CvsRootFieldByFieldConfigurationPanel myCvsRootFieldByFieldConfigurationPanel = new CvsRootFieldByFieldConfigurationPanel();

  public EditCvsConfigurationFieldByFieldDialog(String config) {
    super(true);
    myConfiguration = config;
    myCvsRootFieldByFieldConfigurationPanel.updateFrom(CvsRootDataBuilder.createSettingsOn(myConfiguration, false));
    setTitle(CvsBundle.message("dialog.title.configure.cvs.root.field.by.field"));
    init();
  }

  @Override
  protected void doOKAction() {
    try {
      myConfiguration = myCvsRootFieldByFieldConfigurationPanel.getSettings();
      super.doOKAction();
    }
    catch (InputException ex) {
      ex.show();
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myCvsRootFieldByFieldConfigurationPanel.getPanel();
  }

  public String getConfiguration() {
    return myConfiguration;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCvsRootFieldByFieldConfigurationPanel.getPreferredFocusedComponent();
  }
}
