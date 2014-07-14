/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.maddyhome.idea.copyright.ui;

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

  public String getDisplayName() {
    return "Copyright";
  }

  public String getHelpTopic() {
    return getId();
  }

  public JComponent createComponent() {
    myOptionsPanel = new ProjectSettingsPanel(project, myProfilesPanel);
    myProfilesPanel.setUpdate(new Runnable() {
      @Override
      public void run() {
        reloadProfiles();
      }
    });
    return myOptionsPanel.getMainComponent();
  }

  public boolean isModified() {
    if (myOptionsPanel != null) {
      return myOptionsPanel.isModified();
    }

    return false;
  }

  public void apply() throws ConfigurationException {
    if (myOptionsPanel != null) {
      myOptionsPanel.apply();
    }
  }

  public void reset() {
    if (myOptionsPanel != null) {
      myOptionsPanel.reset();
    }
  }

  public void disposeUIResources() {
    myOptionsPanel = null;
  }

  public boolean hasOwnContent() {
    return true;
  }

  @NotNull
  public String getId() {
    return "copyright";
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  protected Configurable[] buildConfigurables() {
    return new Configurable[]{myProfilesPanel, new CopyrightFormattingConfigurable(project)};
  }

  private void reloadProfiles() {
    if (myOptionsPanel != null) {
      myOptionsPanel.reloadCopyrightProfiles();
    }
  }
  
}
