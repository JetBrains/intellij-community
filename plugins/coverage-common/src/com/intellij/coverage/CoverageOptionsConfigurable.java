// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class CoverageOptionsConfigurable extends CompositeConfigurable<CoverageOptions>implements SearchableConfigurable,
                                                                                                  Configurable.WithEpDependencies {
  private CoverageOptionsPanel myPanel;
  private final CoverageOptionsProvider myManager;
  private final Project myProject;

  public CoverageOptionsConfigurable(Project project) {
    myManager = CoverageOptionsProvider.getInstance(project);
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
    return CoverageBundle.message("configurable.CoverageOptionsConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.project.settings.coverage";
  }

  @Override
  public JComponent createComponent() {
    myPanel = new CoverageOptionsPanel();

    List<JComponent> extensionPanels = collectExtensionOptionsComponents();

    if (!extensionPanels.isEmpty()) {
      return createCompositePanel(extensionPanels);
    }
    else {
      return myPanel.myWholePanel;
    }
  }

  private List<JComponent> collectExtensionOptionsComponents() {
    List<JComponent> additionalPanels = new ArrayList<>();
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
    return CoverageOptions.EP_NAME.getExtensions(myProject);
  }

  @Override
  public boolean isModified() {
    return myManager.getOptionToReplace() != getSelectedValue()
           || myManager.activateViewOnRun() != myPanel.myActivateCoverageViewCB.isSelected()
           || myManager.showInProjectView() != myPanel.myShowInProjectViewCB.isSelected()
           || super.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myManager.setOptionsToReplace(getSelectedValue());
    myManager.setActivateViewOnRun(myPanel.myActivateCoverageViewCB.isSelected());
    myManager.setShowInProjectView(myPanel.myShowInProjectViewCB.isSelected());
    super.apply();
  }

  private int getSelectedValue() {
    if (myPanel.myReplaceRB.isSelected()) {
      return CoverageOptionsProvider.REPLACE_SUITE;
    }
    else if (myPanel.myAddRB.isSelected()) {
      return CoverageOptionsProvider.ADD_SUITE;
    }
    else if (myPanel.myDoNotApplyRB.isSelected()) {
      return CoverageOptionsProvider.IGNORE_SUITE;
    }
    return CoverageOptionsProvider.ASK_ON_NEW_SUITE;
  }

  @Override
  public void reset() {
    final int addOrReplace = myManager.getOptionToReplace();
    final JRadioButton radioButton = switch (addOrReplace) {
      case CoverageOptionsProvider.REPLACE_SUITE -> myPanel.myReplaceRB;
      case CoverageOptionsProvider.ADD_SUITE -> myPanel.myAddRB;
      case CoverageOptionsProvider.IGNORE_SUITE -> myPanel.myDoNotApplyRB;
      default -> myPanel.myShowOptionsRB;
    };
    radioButton.setSelected(true);

    myPanel.myActivateCoverageViewCB.setSelected(myManager.activateViewOnRun());
    myPanel.myShowInProjectViewCB.setSelected(myManager.showInProjectView());
    super.reset();
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
    super.disposeUIResources();
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return Collections.singletonList(CoverageOptions.EP_NAME);
  }

  private static class CoverageOptionsPanel {
    private JRadioButton myShowOptionsRB;
    private JRadioButton myReplaceRB;
    private JRadioButton myAddRB;
    private JRadioButton myDoNotApplyRB;

    private JPanel myWholePanel;
    private JCheckBox myActivateCoverageViewCB;
    private JCheckBox myShowInProjectViewCB;
  }
}
