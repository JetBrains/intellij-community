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
import com.intellij.ui.JBIntSpinner;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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
