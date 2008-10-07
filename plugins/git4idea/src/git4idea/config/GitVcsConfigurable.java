package git4idea.config;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Git VCS configurable implementation
 */
public class GitVcsConfigurable implements Configurable {
  private final GitVcsSettings settings;
  private GitVcsPanel panel;
  private final Project project;

  public GitVcsConfigurable(@NotNull GitVcsSettings settings, @NotNull Project project) {
    this.project = project;
    this.settings = settings;
  }

  /**
   * {@inheritDoc}
   */
  public String getDisplayName() {
    return GitVcs.NAME;
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public Icon getIcon() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public JComponent createComponent() {
    panel = new GitVcsPanel(project);
    panel.load(settings);
    return panel.getPanel();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isModified() {
    return panel.isModified(settings);
  }

  /**
   * {@inheritDoc}
   */
  public void apply() throws ConfigurationException {
    panel.save(settings);
  }

  /**
   * {@inheritDoc}
   */
  public void reset() {
    panel.load(settings);
  }

  /**
   * {@inheritDoc}
   */
  public void disposeUIResources() {
  }
}
