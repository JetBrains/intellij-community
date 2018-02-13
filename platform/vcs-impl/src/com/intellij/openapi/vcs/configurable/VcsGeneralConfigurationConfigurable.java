/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
        public void activeVcsSetChanged(Collection<AbstractVcs> activeVcses) {
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
  @Nls
  public String getDisplayName() {
    return "Confirmation";
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return "project.propVCSSupport.Confirmation";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
