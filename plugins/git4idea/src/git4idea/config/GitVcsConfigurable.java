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
package git4idea.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GitVcsConfigurable implements Configurable {

  private final Project myProject;
  private final GitVcsSettings mySettings;
  private GitVcsPanel panel;

  public GitVcsConfigurable(@NotNull GitVcsSettings settings, @NotNull Project project) {
    myProject = project;
    mySettings = settings;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return GitVcs.NAME;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "project.propVCSSupport.VCSs.Git";
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    panel = new GitVcsPanel(myProject);
    panel.load(mySettings);
    return panel.getPanel();
  }

  @Override
  public boolean isModified() {
    return panel.isModified(mySettings);
  }

  @Override
  public void apply() throws ConfigurationException {
    panel.save(mySettings);
  }

  @Override
  public void reset() {
    panel.load(mySettings);
  }

  @Override
  public void disposeUIResources() {
  }

}
