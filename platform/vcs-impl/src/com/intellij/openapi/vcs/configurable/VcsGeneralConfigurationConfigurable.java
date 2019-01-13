// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

public class VcsGeneralConfigurationConfigurable implements SearchableConfigurable {
  private VcsGeneralConfigurationPanel myPanel;
  private final Project myProject;
  private final VcsManagerConfigurable myMainConfigurable;

  public VcsGeneralConfigurationConfigurable(Project project, VcsManagerConfigurable configurable) {
    myProject = project;
    myMainConfigurable = configurable;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myPanel = new VcsGeneralConfigurationPanel(myProject);
    if (getMappings() != null) {
      myPanel.updateAvailableOptions(getMappings().getActiveVcses());
      addListenerToGeneralPanel();
    }
    else {
      myPanel.updateAvailableOptions(Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss()));
    }
    addListenerToGeneralPanel();

    return myPanel.getPanel();
  }

  private VcsDirectoryConfigurationPanel getMappings() {
    return myMainConfigurable.getMappings();
  }

  private void addListenerToGeneralPanel() {
    VcsDirectoryConfigurationPanel mappings = getMappings();
    if (mappings != null) {
      mappings.addVcsListener(new ModuleVcsListener() {
        @Override
        public void activeVcsSetChanged(Collection<? extends AbstractVcs> activeVcses) {
          myPanel.updateAvailableOptions(activeVcses);
        }
      });
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }
  @Override
  @Nls
  public String getDisplayName() {
    return "Confirmation";
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return "project.propVCSSupport.Confirmation";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
