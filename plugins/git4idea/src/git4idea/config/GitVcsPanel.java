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

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBCheckBox;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.GitBranchSyncSetting;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Git VCS configuration panel
 */
public class GitVcsPanel {

  private static final String IDEA_SSH = GitBundle.getString("git.vcs.config.ssh.mode.idea"); // IDEA ssh value
  private static final String NATIVE_SSH = GitBundle.getString("git.vcs.config.ssh.mode.native"); // Native SSH value

  private final GitVcsApplicationSettings myAppSettings;
  private final GitVcs myVcs;

  private JButton myTestButton; // Test git executable
  private JComponent myRootPanel;
  private TextFieldWithBrowseButton myGitField;
  private JComboBox mySSHExecutableComboBox; // Type of SSH executable to use
  private JCheckBox myAutoUpdateIfPushRejected;
  private JBCheckBox mySyncBranchControl;
  private JCheckBox myAutoCommitOnCherryPick;
  private JBCheckBox myWarnAboutCrlf;

  public GitVcsPanel(@NotNull Project project) {
    myVcs = GitVcs.getInstance(project);
    myAppSettings = GitVcsApplicationSettings.getInstance();
    mySSHExecutableComboBox.addItem(IDEA_SSH);
    mySSHExecutableComboBox.addItem(NATIVE_SSH);
    mySSHExecutableComboBox.setSelectedItem(IDEA_SSH);
    mySSHExecutableComboBox
      .setToolTipText(GitBundle.message("git.vcs.config.ssh.mode.tooltip", ApplicationNamesInfo.getInstance().getFullProductName()));
    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        testConnection();
      }
    });
    myGitField.addBrowseFolderListener(GitBundle.getString("find.git.title"), GitBundle.getString("find.git.description"), project,
                                       FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    final GitRepositoryManager repositoryManager = ServiceManager.getService(project, GitRepositoryManager.class);
    mySyncBranchControl.setVisible(repositoryManager != null && repositoryManager.moreThanOneRoot());
  }

  /**
   * Test availability of the connection
   */
  private void testConnection() {
    final String executable = getCurrentExecutablePath();
    if (myAppSettings != null) {
      myAppSettings.setPathToGit(executable);
    }
    final GitVersion version;
    try {
      version = GitVersion.identifyVersion(executable);
    } catch (Exception e) {
      Messages.showErrorDialog(myRootPanel, e.getMessage(), GitBundle.getString("find.git.error.title"));
      return;
    }

    if (version.isSupported()) {
      Messages.showInfoMessage(myRootPanel,
                               String.format("<html>%s<br>Git version is %s</html>", GitBundle.getString("find.git.success.title"),
                                             version.toString()),
                               GitBundle.getString("find.git.success.title"));
    } else {
      Messages.showWarningDialog(myRootPanel, GitBundle.message("find.git.unsupported.message", version.toString(), GitVersion.MIN),
                                 GitBundle.getString("find.git.success.title"));
    }
  }

  private String getCurrentExecutablePath() {
    return myGitField.getText().trim();
  }

  /**
   * @return the configuration panel
   */
  public JComponent getPanel() {
    return myRootPanel;
  }

  /**
   * Load settings into the configuration panel
   *
   * @param settings the settings to load
   */
  public void load(@NotNull GitVcsSettings settings) {
    myGitField.setText(settings.getAppSettings().getPathToGit());
    mySSHExecutableComboBox.setSelectedItem(settings.isIdeaSsh() ? IDEA_SSH : NATIVE_SSH);
    myAutoUpdateIfPushRejected.setSelected(settings.autoUpdateIfPushRejected());
    mySyncBranchControl.setSelected(settings.getSyncSetting() == GitBranchSyncSetting.SYNC);
    myAutoCommitOnCherryPick.setSelected(settings.isAutoCommitOnCherryPick());
    myWarnAboutCrlf.setSelected(settings.warnAboutCrlf());
  }

  /**
   * Check if fields has been modified with respect to settings object
   *
   * @param settings the settings to load
   */
  public boolean isModified(@NotNull GitVcsSettings settings) {
    return !settings.getAppSettings().getPathToGit().equals(getCurrentExecutablePath()) ||
           (settings.isIdeaSsh() != IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem())) ||
           !settings.autoUpdateIfPushRejected() == myAutoUpdateIfPushRejected.isSelected() ||
           ((settings.getSyncSetting() == GitBranchSyncSetting.SYNC) != mySyncBranchControl.isSelected() ||
           settings.isAutoCommitOnCherryPick() != myAutoCommitOnCherryPick.isSelected() ||
           settings.warnAboutCrlf() != myWarnAboutCrlf.isSelected());
  }

  /**
   * Save configuration panel state into settings object
   *
   * @param settings the settings object
   */
  public void save(@NotNull GitVcsSettings settings) {
    settings.getAppSettings().setPathToGit(getCurrentExecutablePath());
    myVcs.checkVersion();
    settings.getAppSettings().setIdeaSsh(IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem()) ?
                                         GitVcsApplicationSettings.SshExecutable.IDEA_SSH :
                                         GitVcsApplicationSettings.SshExecutable.NATIVE_SSH);
    settings.setAutoUpdateIfPushRejected(myAutoUpdateIfPushRejected.isSelected());

    settings.setSyncSetting(mySyncBranchControl.isSelected() ? GitBranchSyncSetting.SYNC : GitBranchSyncSetting.DONT);
    settings.setAutoCommitOnCherryPick(myAutoCommitOnCherryPick.isSelected());
    settings.setWarnAboutCrlf(myWarnAboutCrlf.isSelected());
  }

}
