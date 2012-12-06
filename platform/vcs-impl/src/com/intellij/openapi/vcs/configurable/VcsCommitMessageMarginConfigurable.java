/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class VcsCommitMessageMarginConfigurable implements Configurable {

  private JCheckBox myEnableMarginCheckbox;
  private JTextField myRightMarginTextField;
  private final VcsConfiguration myConfiguration;

  public VcsCommitMessageMarginConfigurable(Project project) {
    myConfiguration = VcsConfiguration.getInstance(project);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return VcsBundle.message("configuration.commit.message.margin.title");
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    myEnableMarginCheckbox = new JCheckBox(VcsBundle.message("configuration.commit.message.margin.prompt"), myConfiguration.USE_COMMIT_MESSAGE_MARGIN);
    myRightMarginTextField = new JTextField(Integer.toString(myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE), 5);
    myRightMarginTextField.setEnabled(myEnableMarginCheckbox.isSelected());
    wrapper.add(myEnableMarginCheckbox);
    wrapper.add(myRightMarginTextField);

    myEnableMarginCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myRightMarginTextField.setEnabled(myEnableMarginCheckbox.isSelected());
      }
    });

    return wrapper;
  }

  @Override
  public boolean isModified() {
    if (myEnableMarginCheckbox.isSelected() != myConfiguration.USE_COMMIT_MESSAGE_MARGIN) {
      return true;
    }

    if (getValidRightMargin() == null || getValidRightMargin() != myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE) {
      return true;
    }

    return false;
  }

  /**
   * If possible, returns a valid user-input right margin (i.e. an int greater than zero).
   * Otherwise, returns null.
   */
  private Integer getValidRightMargin() {
    if (myRightMarginTextField == null) {
      return null;
    }

    try {
      Integer rightMargin = Integer.parseInt(myRightMarginTextField.getText());
      return rightMargin == null  || rightMargin < 0 ? null : rightMargin;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfiguration.USE_COMMIT_MESSAGE_MARGIN = myEnableMarginCheckbox.isSelected();
    Integer rightMargin = getValidRightMargin();
    if (rightMargin == null) {
      // invalid right margin provided; revert to previous setting
      myRightMarginTextField.setText(Integer.toString(myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE));
    } else {
      myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE = rightMargin;
    }
  }

  @Override
  public void reset() {
    myEnableMarginCheckbox.setSelected(myConfiguration.USE_COMMIT_MESSAGE_MARGIN);
    myRightMarginTextField.setText(Integer.toString(myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE));
  }

  @Override
  public void disposeUIResources() {
  }
}
