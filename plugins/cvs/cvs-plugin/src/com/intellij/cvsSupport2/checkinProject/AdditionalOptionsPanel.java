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
package com.intellij.cvsSupport2.checkinProject;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.TagNameFieldOwner;
import com.intellij.cvsSupport2.ui.experts.importToCvs.CvsFieldValidator;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AdditionalOptionsPanel implements RefreshableOnComponent, TagNameFieldOwner {

  private JPanel myPanel;
  private JTextField myTagName;
  private JCheckBox myTag;
  private JLabel myErrorLabel;

  private final CvsConfiguration myConfiguration;
  private boolean myIsCorrect = true;
  private String myErrorMessage;
  private JCheckBox myOverrideExisting;

  public AdditionalOptionsPanel(CvsConfiguration configuration) {
    myConfiguration = configuration;
    myTag.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateEnable();
      }
    });
    CvsFieldValidator.installOn(this, myTagName, myErrorLabel, new AbstractButton[]{myTag});
  }

  private void updateEnable() {
    boolean tag = myTag.isSelected();
    myTagName.setEditable(tag);
    myOverrideExisting.setEnabled(tag);
  }

  public void refresh() {
    myTagName.setText(myConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME);
    myOverrideExisting.setSelected(myConfiguration.OVERRIDE_EXISTING_TAG_FOR_PROJECT);
    updateEnable();
  }

  public void saveState() {
    if (!myIsCorrect) {
      throw new InputException(CvsBundle.message("error.message.incorrect.tag.name", myErrorMessage), myTagName);
    }
    myConfiguration.TAG_AFTER_PROJECT_COMMIT = myTag.isSelected();
    myConfiguration.OVERRIDE_EXISTING_TAG_FOR_PROJECT = myOverrideExisting.isSelected();
    myConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME = myTagName.getText().trim();
  }

  public void restoreState() {
    refresh();
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void enableOkAction() {
    myIsCorrect = true;
  }

  public void disableOkAction(String errorMessage) {
    myIsCorrect = false;
    myErrorMessage = errorMessage;
  }

  public boolean tagFieldIsActive() {
    return myTag.isSelected();
  }
}