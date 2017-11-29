/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.commit;

import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class BodyLimitInspectionOptions implements ConfigurableUi<Project> {
  @NotNull private final BodyLimitInspection myInspection;
  private JBIntSpinner myMarginSpinner;
  private JPanel myMainPanel;
  private JBCheckBox myShowRightMargin;
  private JBCheckBox myWrapWhenTyping;

  public BodyLimitInspectionOptions(@NotNull BodyLimitInspection inspection) {
    myInspection = inspection;
  }

  private void createUIComponents() {
    myMarginSpinner = new JBIntSpinner(0, 0, 10000);
  }

  @Override
  public void reset(@NotNull Project project) {
    VcsConfiguration settings = VcsConfiguration.getInstance(project);

    myMarginSpinner.setNumber(myInspection.RIGHT_MARGIN);
    myShowRightMargin.setSelected(settings.USE_COMMIT_MESSAGE_MARGIN);
    myWrapWhenTyping.setSelected(settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN);
  }

  @Override
  public boolean isModified(@NotNull Project project) {
    VcsConfiguration settings = VcsConfiguration.getInstance(project);

    return myMarginSpinner.getNumber() != myInspection.RIGHT_MARGIN ||
           myShowRightMargin.isSelected() != settings.USE_COMMIT_MESSAGE_MARGIN ||
           myWrapWhenTyping.isSelected() != settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
  }

  @Override
  public void apply(@NotNull Project project) {
    VcsConfiguration settings = VcsConfiguration.getInstance(project);

    myInspection.RIGHT_MARGIN = myMarginSpinner.getNumber();
    settings.USE_COMMIT_MESSAGE_MARGIN = myShowRightMargin.isSelected();
    settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = myWrapWhenTyping.isSelected();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }
}
