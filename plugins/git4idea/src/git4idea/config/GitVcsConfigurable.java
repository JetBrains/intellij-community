/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.config;

import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.project.Project;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

public class GitVcsConfigurable extends ConfigurableBase<GitVcsPanel, GitVcsConfigurable.GitVcsSettingsHolder> {
  private final Project myProject;
  private final GitVcsSettingsHolder mySettingsHolder;

  public GitVcsConfigurable(@NotNull GitVcsApplicationSettings applicationSettings,
                            @NotNull Project project,
                            @NotNull GitVcsSettings projectSettings,
                            @NotNull GitSharedSettings sharedSettings) {
    super("vcs." + GitVcs.NAME, GitVcs.NAME, "project.propVCSSupport.VCSs.Git");
    myProject = project;
    mySettingsHolder = new GitVcsSettingsHolder(applicationSettings, projectSettings, sharedSettings);
  }

  @Override
  protected GitVcsPanel createUi() {
    return new GitVcsPanel(myProject);
  }

  @NotNull
  @Override
  protected GitVcsSettingsHolder getSettings() {
    return mySettingsHolder;
  }

  static class GitVcsSettingsHolder {
    @NotNull private final GitVcsApplicationSettings myApplicationSettings;
    @NotNull private final GitVcsSettings myProjectSettings;
    @NotNull private final GitSharedSettings mySharedSettings;

    GitVcsSettingsHolder(@NotNull GitVcsApplicationSettings applicationSettings,
                         @NotNull GitVcsSettings projectSettings,
                         @NotNull GitSharedSettings sharedSettings) {
      myApplicationSettings = applicationSettings;
      myProjectSettings = projectSettings;
      mySharedSettings = sharedSettings;
    }

    @NotNull
    public GitVcsApplicationSettings getApplicationSettings() {
      return myApplicationSettings;
    }

    @NotNull
    public GitVcsSettings getProjectSettings() {
      return myProjectSettings;
    }

    @NotNull
    public GitSharedSettings getSharedSettings() {
      return mySharedSettings;
    }
  }
}
