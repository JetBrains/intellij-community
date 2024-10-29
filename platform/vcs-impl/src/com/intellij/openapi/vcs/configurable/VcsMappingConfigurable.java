// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public class VcsMappingConfigurable implements SearchableConfigurable {
  private static final String ID = "project.propVCSSupport.DirectoryMappings";
  public static final String HELP_ID = "project.propVCSSupport.Mappings";

  private final Project myProject;

  @Nullable private VcsDirectoryConfigurationPanel myPanel;

  public VcsMappingConfigurable(@NotNull Project project) {
    myProject = project;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return VcsBundle.message("configurable.VcsDirectoryConfigurationPanel.display.name");
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public @Nullable String getHelpTopic() {
    return HELP_ID;
  }

  @Override
  public JComponent createComponent() {
    if (myPanel == null) myPanel = new VcsDirectoryConfigurationPanel(myProject);
    return myPanel;
  }

  @Override
  public void disposeUIResources() {
    if (myPanel != null) {
      Disposer.dispose(myPanel);
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
