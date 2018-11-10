// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;


import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class VcsBackgroundOperationsConfigurable implements SearchableConfigurable {
  private VcsBackgroundOperationsConfigurationPanel myPanel;
  private final Project myProject;

  public VcsBackgroundOperationsConfigurable(Project project) {
    myProject = project;
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Background";
  }

  @Override
  public String getHelpTopic() {
    return "project.propVCSSupport.Background";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
    myPanel = new VcsBackgroundOperationsConfigurationPanel(myProject);
    return myPanel.getPanel();
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

}
