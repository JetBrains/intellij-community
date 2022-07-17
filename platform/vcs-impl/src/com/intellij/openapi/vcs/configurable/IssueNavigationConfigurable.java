// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class IssueNavigationConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final Project myProject;

  @Nullable private IssueNavigationConfigurationPanel myPanel;

  public IssueNavigationConfigurable(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public String getDisplayName() {
    return VcsBundle.message("configurable.IssueNavigationConfigurationPanel.display.name");
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "project.propVCSSupport.Issue.Navigation";
  }

  @NotNull
  @Override
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
    if (myPanel == null) myPanel = new IssueNavigationConfigurationPanel(myProject);
    return myPanel;
  }

  @Override
  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel = null;
    }
  }

  @Override
  public void reset() {
    if (myPanel != null) {
      myPanel.reset();
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myPanel != null) {
      myPanel.apply();
    }
  }

  @Override
  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }
}
