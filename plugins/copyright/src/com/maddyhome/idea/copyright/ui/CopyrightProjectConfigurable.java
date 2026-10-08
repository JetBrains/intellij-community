// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.maddyhome.idea.copyright.ui;

import com.intellij.copyright.CopyrightBundle;
import com.intellij.copyright.CopyrightManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class CopyrightProjectConfigurable extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll{
  public static final String ID = "copyright";
  private final Project project;
  private ProjectSettingsPanel myOptionsPanel = null;
  private final CopyrightProfilesConfigurable myProfilesConfigurable;

  public CopyrightProjectConfigurable(Project project) {
    this.project = project;
    myProfilesConfigurable = new CopyrightProfilesConfigurable(project);
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
    CopyrightProfilesPanel profilesPanel = myProfilesConfigurable.getOrCreatePanel();
    myOptionsPanel = new ProjectSettingsPanel(project, profilesPanel);
    profilesPanel.setUpdate(this::reloadProfiles);
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
  public @NotNull String getId() {
    return ID;
  }

  @Override
  protected @NotNull Configurable @NotNull [] buildConfigurables() {
    return new Configurable[]{myProfilesConfigurable, new CopyrightFormattingConfigurable(project)};
  }

  private void reloadProfiles() {
    if (myOptionsPanel != null) {
      myOptionsPanel.reloadCopyrightProfiles();
    }
  }

  public boolean hasAnyCopyrights() {
    if (myOptionsPanel != null) {
      return myOptionsPanel.hasAnyCopyrights();
    }
    return CopyrightManager.getInstance(project).hasAnyCopyrights();
  }
}
