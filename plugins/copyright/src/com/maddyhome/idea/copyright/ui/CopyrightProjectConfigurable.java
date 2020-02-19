// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.ui;

import com.intellij.copyright.CopyrightBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CopyrightProjectConfigurable extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll{
  private final Project project;
  private ProjectSettingsPanel myOptionsPanel = null;
  private final CopyrightProfilesPanel myProfilesPanel;

  public CopyrightProjectConfigurable(Project project) {
    this.project = project;
    myProfilesPanel = new CopyrightProfilesPanel(project);
  }

  @Override
  public String getDisplayName() {
    return CopyrightBundle.message("configurable.CopyrightProjectConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return getId();
  }

  @Override
  public JComponent createComponent() {
    myOptionsPanel = new ProjectSettingsPanel(project, myProfilesPanel);
    myProfilesPanel.setUpdate(this::reloadProfiles);
    return myOptionsPanel.getMainComponent();
  }

  @Override
  public boolean isModified() {
    if (myOptionsPanel != null) {
      return myOptionsPanel.isModified();
    }

    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myOptionsPanel != null) {
      myOptionsPanel.apply();
    }
  }

  @Override
  public void reset() {
    if (myOptionsPanel != null) {
      myOptionsPanel.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    myOptionsPanel = null;
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  @NotNull
  public String getId() {
    return "copyright";
  }

  @Override
  protected Configurable[] buildConfigurables() {
    return new Configurable[]{myProfilesPanel, new CopyrightFormattingConfigurable(project)};
  }

  private void reloadProfiles() {
    if (myOptionsPanel != null) {
      myOptionsPanel.reloadCopyrightProfiles();
    }
  }

}
