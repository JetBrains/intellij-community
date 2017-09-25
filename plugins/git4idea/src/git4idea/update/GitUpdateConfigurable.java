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
package git4idea.update;

import com.intellij.openapi.options.Configurable;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * Configurable for the update session
 */
public class GitUpdateConfigurable implements Configurable {
  /** The vcs settings for the configurable */
  private final GitVcsSettings mySettings;
  /** The options panel */
  private GitUpdateOptionsPanel myPanel;

  /**
   * The constructor
   * @param settings the settings object
   */
  public GitUpdateConfigurable(GitVcsSettings settings) {
    mySettings = settings;
  }

  /**
   * {@inheritDoc}
   */
  @Nls
  public String getDisplayName() {
    return GitBundle.getString("update.options.display.name");
  }

  /**
   * {@inheritDoc}
   */
  public String getHelpTopic() {
    return "reference.VersionControl.Git.UpdateProject";
  }

  /**
   * {@inheritDoc}
   */
  public JComponent createComponent() {
    myPanel = new GitUpdateOptionsPanel();
    return myPanel.getPanel();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isModified() {
    return myPanel.isModified(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  public void apply() {
    myPanel.applyTo(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  public void reset() {
    myPanel.updateFrom(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  public void disposeUIResources() {
    myPanel = null;
  }
}
