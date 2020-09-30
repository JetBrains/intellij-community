// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.ui.HgConfigurationProjectPanel;

public final class HgProjectConfigurable extends ConfigurableBase<HgConfigurationProjectPanel, HgProjectConfigurable.HgSettingsHolder> {
  @NotNull private final Project myProject;
  @NotNull private final HgSettingsHolder mySettingsHolder;

  public HgProjectConfigurable(@NotNull Project project) {
    super("vcs.Mercurial", getDISPLAY_NAME(), "project.propVCSSupport.VCSs.Mercurial");
    myProject = project;
    mySettingsHolder = new HgSettingsHolder(HgGlobalSettings.getInstance(), HgProjectSettings.getInstance(myProject));
  }

  @NotNull
  @Override
  protected HgSettingsHolder getSettings() {
    return mySettingsHolder;
  }

  @Override
  protected HgConfigurationProjectPanel createUi() {
    return new HgConfigurationProjectPanel(myProject);
  }

  public static final class HgSettingsHolder {
    @NotNull private final HgGlobalSettings myGlobalSettings;
    @NotNull private final HgProjectSettings myProjectSettings;

    HgSettingsHolder(@NotNull HgGlobalSettings globalSettings, @NotNull HgProjectSettings projectSettings) {
      myGlobalSettings = globalSettings;
      myProjectSettings = projectSettings;
    }

    @NotNull
    public HgGlobalSettings getGlobalSettings() {
      return myGlobalSettings;
    }

    @NotNull
    public HgProjectSettings getProjectSettings() {
      return myProjectSettings;
    }
  }

  @Nls
  public static String getDISPLAY_NAME() {
    return HgBundle.message("hg4idea.mercurial");
  }
}
