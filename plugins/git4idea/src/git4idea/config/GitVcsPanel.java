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

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Git VCS configuration panel
 */
public class GitVcsPanel {
  /**
   * Test git executable button
   */
  private JButton myTestButton;
  /**
   * The root panel
   */
  private JComponent myPanel;
  /**
   * The git path field
   */
  private TextFieldWithBrowseButton myGitField;
  /**
   * Type of SSH executable to use
   */
  private JComboBox mySSHExecutableComboBox;
  /**
   * The project
   */
  private final Project myProject;
  /**
   * The settings component
   */
  private final GitVcsSettings mySettings;
  /**
   * IDEA ssh value
   */
  private static final String IDEA_SSH = GitBundle.getString("git.vcs.config.ssh.mode.idea");
  /**
   * Native SSH value
   */
  private static final String NATIVE_SSH = GitBundle.getString("git.vcs.config.ssh.mode.native");

  /**
   * The constructor
   *
   * @param project the context project
   */
  public GitVcsPanel(@NotNull Project project) {
    mySettings = GitVcsSettings.getInstance(project);
    myProject = project;
    mySSHExecutableComboBox.addItem(IDEA_SSH);
    mySSHExecutableComboBox.addItem(NATIVE_SSH);
    mySSHExecutableComboBox.setSelectedItem(GitVcsSettings.isDefaultIdeaSsh() ? IDEA_SSH : NATIVE_SSH);
    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        testConnection();
      }
    });

    myGitField.addBrowseFolderListener(GitBundle.getString("find.git.title"), GitBundle.getString("find.git.description"), project,
                                       new FileChooserDescriptor(true, false, false, false, false, false));
  }

  /**
   * Test availability of the connection
   */
  private void testConnection() {
    mySettings.GIT_EXECUTABLE = myGitField.getText();
    final String s;
    try {
      s = GitVcs.version(myProject);
    }
    catch (VcsException e) {
      Messages.showErrorDialog(myProject, e.getMessage(), GitBundle.getString("find.git.error.title"));
      return;
    }
    if (GitVersion.parse(s).isSupported()) {
      Messages.showInfoMessage(myProject, s, GitBundle.getString("find.git.success.title"));
    }
    else {
      Messages.showWarningDialog(myProject, GitBundle.message("find.git.unsupported.message", s, GitVersion.MIN),
                                 GitBundle.getString("find.git.success.title"));
    }
  }

  /**
   * @return the configuration panel
   */
  public JComponent getPanel() {
    return myPanel;
  }

  /**
   * Load settings into the configuration panel
   *
   * @param settings the settings to load
   */
  public void load(@NotNull GitVcsSettings settings) {
    myGitField.setText(settings.GIT_EXECUTABLE);
    mySSHExecutableComboBox.setSelectedItem(settings.isIdeaSsh() ? IDEA_SSH : NATIVE_SSH);
  }

  /**
   * Check if fields has been modified with respect to settings object
   *
   * @param settings the settings to load
   */
  public boolean isModified(@NotNull GitVcsSettings settings) {
    return !settings.GIT_EXECUTABLE.equals(myGitField.getText()) ||
           (settings.isIdeaSsh() != IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem()));
  }

  /**
   * Save configuration panel state into settings object
   *
   * @param settings the settings object
   */
  public void save(@NotNull GitVcsSettings settings) {
    settings.GIT_EXECUTABLE = myGitField.getText();
    settings.setIdeaSsh(IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem()));
  }
}
