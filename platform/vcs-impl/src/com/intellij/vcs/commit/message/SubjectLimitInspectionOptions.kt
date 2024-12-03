// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message;

import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBIntSpinner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public class SubjectLimitInspectionOptions implements ConfigurableUi<Project> {
  @NotNull private final SubjectLimitInspection myInspection;
  private JBIntSpinner myMarginSpinner;
  private JPanel myMainPanel;

  public SubjectLimitInspectionOptions(@NotNull SubjectLimitInspection inspection) {
    myInspection = inspection;
  }

  private void createUIComponents() {
    myMarginSpinner = new JBIntSpinner(0, 0, 10000);
  }

  @Override
  public void reset(@NotNull Project project) {
    myMarginSpinner.setNumber(myInspection.RIGHT_MARGIN);
  }

  @Override
  public boolean isModified(@NotNull Project project) {
    return myMarginSpinner.getNumber() != myInspection.RIGHT_MARGIN;
  }

  @Override
  public void apply(@NotNull Project project) {
    myInspection.RIGHT_MARGIN = myMarginSpinner.getNumber();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }
}
