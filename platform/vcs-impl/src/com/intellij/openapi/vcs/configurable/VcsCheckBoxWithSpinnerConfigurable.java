/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/28/12
 * Time: 2:41 PM
 */
public abstract class VcsCheckBoxWithSpinnerConfigurable implements Configurable {
  protected final Project myProject;
  private final String myCheckboxText;
  private final String myMeasure;
  protected JCheckBox myHighlightRecentlyChanged;
  protected JSpinner myHighlightInterval;

  public VcsCheckBoxWithSpinnerConfigurable(Project project, final String checkboxText, final String measure) {
    myProject = project;
    myCheckboxText = checkboxText;
    myMeasure = measure;
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    myHighlightRecentlyChanged = new JCheckBox(myCheckboxText);
    myHighlightInterval = new JSpinner(createSpinnerModel());
    wrapper.add(myHighlightRecentlyChanged);
    wrapper.add(myHighlightInterval);
    final JLabel days = new JLabel(myMeasure);
    days.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));
    wrapper.add(days);

    myHighlightRecentlyChanged.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myHighlightInterval.setEnabled(myHighlightRecentlyChanged.isSelected());
      }
    });
    return wrapper;
  }

  protected abstract SpinnerNumberModel createSpinnerModel();

  @Override
  public void disposeUIResources() {
  }
}
