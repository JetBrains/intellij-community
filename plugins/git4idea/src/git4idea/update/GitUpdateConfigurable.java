// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Override
  @Nls
  public String getDisplayName() {
    return GitBundle.getString("update.options.display.name");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getHelpTopic() {
    return "reference.VersionControl.Git.UpdateProject";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public JComponent createComponent() {
    myPanel = new GitUpdateOptionsPanel();
    return myPanel.getPanel();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isModified() {
    return myPanel.isModified(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void apply() {
    myPanel.applyTo(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    myPanel.updateFrom(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void disposeUIResources() {
    myPanel = null;
  }
}
