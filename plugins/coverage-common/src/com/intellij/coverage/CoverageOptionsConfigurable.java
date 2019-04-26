// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class CoverageOptionsConfigurable extends CompositeConfigurable<CoverageOptions> implements SearchableConfigurable {
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
    for (CoverageOptions coverageOptions : getConfigurables()) {
      additionalPanels.add(coverageOptions.createComponent());
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

  @NotNull
  @Override
  protected List<CoverageOptions> createConfigurables() {
    return CoverageOptions.EP_NAME.getExtensionList(myProject);
  }

  @Override
  public boolean isModified() {
    if (myManager.getOptionToReplace() != getSelectedValue()) {
      return true;
    }

    if (myManager.activateViewOnRun() != myPanel.myActivateCoverageViewCB.isSelected()) {
      return true;
    }

    return super.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myManager.setOptionsToReplace(getSelectedValue());
    myManager.setActivateViewOnRun(myPanel.myActivateCoverageViewCB.isSelected());
    super.apply();
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
    super.reset();
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
    super.disposeUIResources();
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
