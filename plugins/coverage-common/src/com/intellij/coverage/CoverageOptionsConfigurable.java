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
package com.intellij.coverage;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * User: anna
 * Date: 12/16/10
 */
public class CoverageOptionsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private CoverageOptionsPanel myPanel;
  private final CoverageOptionsProvider myManager;
  private final Project myProject;

  public CoverageOptionsConfigurable(CoverageOptionsProvider manager, Project project) {
    myManager = manager;
    myProject = project;
  }

  @NotNull
  @Override
  public String getId() {
    return "coverage";
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Coverage";
  }

  @Override
  public String getHelpTopic() {
    return "reference.project.settings.coverage";
  }

  @Override
  public JComponent createComponent() {
    myPanel = new CoverageOptionsPanel();

    List<JComponent> extensionPanels = collectExtensionOptionsComponents();

    if (extensionPanels.size() > 0) {
      return createCompositePanel(extensionPanels);
    }
    else {
      return myPanel.myWholePanel;
    }
  }

  private List<JComponent> collectExtensionOptionsComponents() {
    List<JComponent> additionalPanels = ContainerUtil.newArrayList();
    for (CoverageOptions coverageOptions : getExtensions()) {
      additionalPanels.add(coverageOptions.getComponent());
    }
    return additionalPanels;
  }

  private JComponent createCompositePanel(List<JComponent> additionalPanels) {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints c = new GridBagConstraints();
    c.anchor = GridBagConstraints.NORTHWEST;
    c.gridx = 0;
    c.gridy = GridBagConstraints.RELATIVE;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    panel.add(myPanel.myWholePanel, c);
    for (JComponent p : additionalPanels) {
      panel.add(p, c);
    }
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1;
    c.weighty = 1;
    panel.add(Box.createVerticalBox(), c);
    return panel;
  }

  private CoverageOptions[] getExtensions() {
    return Extensions.getExtensions(CoverageOptions.EP_NAME, myProject);
  }

  @Override
  public boolean isModified() {
    if (myManager.getOptionToReplace() != getSelectedValue()) {
      return true;
    }

    if (myManager.activateViewOnRun() != myPanel.myActivateCoverageViewCB.isSelected()) {
      return true;
    }

    for (CoverageOptions coverageOptions : getExtensions()) {
      if (coverageOptions.isModified()) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    myManager.setOptionsToReplace(getSelectedValue());
    myManager.setActivateViewOnRun(myPanel.myActivateCoverageViewCB.isSelected());
    for (CoverageOptions coverageOptions : getExtensions()) {
      coverageOptions.apply();
    }
  }

  private int getSelectedValue() {
    if (myPanel.myReplaceRB.isSelected()) {
      return 0;
    }
    else if (myPanel.myAddRB.isSelected()) {
      return 1;
    }
    else if (myPanel.myDoNotApplyRB.isSelected()) {
      return 2;
    }
    return 3;
  }

  @Override
  public void reset() {
    final int addOrReplace = myManager.getOptionToReplace();
    switch (addOrReplace) {
      case 0:
        myPanel.myReplaceRB.setSelected(true);
        break;
      case 1:
        myPanel.myAddRB.setSelected(true);
        break;
      case 2:
        myPanel.myDoNotApplyRB.setSelected(true);
        break;
      default:
        myPanel.myShowOptionsRB.setSelected(true);
    }

    myPanel.myActivateCoverageViewCB.setSelected(myManager.activateViewOnRun());
    for (CoverageOptions coverageOptions : getExtensions()) {
      coverageOptions.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;

    for (CoverageOptions coverageOptions : getExtensions()) {
      coverageOptions.disposeUIResources();
    }
  }

  private static class CoverageOptionsPanel {
    private JRadioButton myShowOptionsRB;
    private JRadioButton myReplaceRB;
    private JRadioButton myAddRB;
    private JRadioButton myDoNotApplyRB;

    private JPanel myWholePanel;
    private JCheckBox myActivateCoverageViewCB;
  }
}
