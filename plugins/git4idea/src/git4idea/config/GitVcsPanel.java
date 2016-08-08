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

import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

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
  private JBCheckBox mySyncControl;
  private JCheckBox myAutoCommitOnCherryPick;
  private JBCheckBox myWarnAboutCrlf;
  private JCheckBox myWarnAboutDetachedHead;
  private JCheckBox myEnableForcePush;
  private TextFieldWithBrowseButton myProtectedBranchesButton;
  private JBLabel myProtectedBranchesLabel;
  private JComboBox myUpdateMethodComboBox;

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
    if (!project.isDefault()) {
      final GitRepositoryManager repositoryManager = ServiceManager.getService(project, GitRepositoryManager.class);
      mySyncControl.setVisible(repositoryManager != null && repositoryManager.moreThanOneRoot());
    }
    else {
      mySyncControl.setVisible(true);
    }
    mySyncControl.setToolTipText(DvcsBundle.message("sync.setting.description", "Git"));
    myProtectedBranchesLabel.setLabelFor(myProtectedBranchesButton);
    myEnableForcePush.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        UIUtil.setEnabled(myProtectedBranchesButton, myEnableForcePush.isSelected(), true);
        UIUtil.setEnabled(myProtectedBranchesLabel, myEnableForcePush.isSelected(), false);
      }
    });
  }

  /**
   * Test availability of the connection
   */
  private void testConnection() {
    final String executable = getCurrentExecutablePath();
    if (myAppSettings != null) {
      myAppSettings.setPathToGit(executable);
    }
    GitVersion version;
    try {
      version = ProgressManager.getInstance().runProcessWithProgressSynchronously(new ThrowableComputable<GitVersion, Exception>() {
        @Override
        public GitVersion compute() throws Exception {
          return GitVersion.identifyVersion(executable);
        }
      }, "Testing Git Executable...", true, myVcs.getProject());
    }
    catch (ProcessCanceledException pce) {
      return;
    }
    catch (Exception e) {
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
  public void load(@NotNull GitVcsSettings settings, @NotNull GitSharedSettings sharedSettings) {
    myGitField.setText(settings.getAppSettings().getPathToGit());
    mySSHExecutableComboBox.setSelectedItem(settings.isIdeaSsh() ? IDEA_SSH : NATIVE_SSH);
    myAutoUpdateIfPushRejected.setSelected(settings.autoUpdateIfPushRejected());
    mySyncControl.setSelected(settings.getSyncSetting() == DvcsSyncSettings.Value.SYNC);
    myAutoCommitOnCherryPick.setSelected(settings.isAutoCommitOnCherryPick());
    myWarnAboutCrlf.setSelected(settings.warnAboutCrlf());
    myWarnAboutDetachedHead.setSelected(settings.warnAboutDetachedHead());
    myEnableForcePush.setSelected(settings.isForcePushAllowed());
    myUpdateMethodComboBox.setSelectedItem(settings.getUpdateType());
    myProtectedBranchesButton.setText(ParametersListUtil.COLON_LINE_JOINER.fun(sharedSettings.getForcePushProhibitedPatterns()));
  }

  /**
   * Check if fields has been modified with respect to settings object
   *
   * @param settings the settings to load
   */
  public boolean isModified(@NotNull GitVcsSettings settings, @NotNull GitSharedSettings sharedSettings) {
    return !settings.getAppSettings().getPathToGit().equals(getCurrentExecutablePath()) ||
           (settings.isIdeaSsh() != IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem())) ||
           !settings.autoUpdateIfPushRejected() == myAutoUpdateIfPushRejected.isSelected() ||
           ((settings.getSyncSetting() == DvcsSyncSettings.Value.SYNC) != mySyncControl.isSelected() ||
           settings.isAutoCommitOnCherryPick() != myAutoCommitOnCherryPick.isSelected() ||
           settings.warnAboutCrlf() != myWarnAboutCrlf.isSelected() ||
           settings.warnAboutDetachedHead() != myWarnAboutDetachedHead.isSelected() ||
           settings.isForcePushAllowed() != myEnableForcePush.isSelected() ||
           settings.getUpdateType() != myUpdateMethodComboBox.getModel().getSelectedItem() ||
           !ContainerUtil.sorted(sharedSettings.getForcePushProhibitedPatterns()).equals(
            ContainerUtil.sorted(getProtectedBranchesPatterns())));
  }

  /**
   * Save configuration panel state into settings object
   *
   * @param settings the settings object
   */
  public void save(@NotNull GitVcsSettings settings, GitSharedSettings sharedSettings) {
    settings.getAppSettings().setPathToGit(getCurrentExecutablePath());
    myVcs.checkVersion();
    settings.getAppSettings().setIdeaSsh(IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem()) ?
                                         GitVcsApplicationSettings.SshExecutable.IDEA_SSH :
                                         GitVcsApplicationSettings.SshExecutable.NATIVE_SSH);
    settings.setAutoUpdateIfPushRejected(myAutoUpdateIfPushRejected.isSelected());

    settings.setSyncSetting(mySyncControl.isSelected() ? DvcsSyncSettings.Value.SYNC : DvcsSyncSettings.Value.DONT_SYNC);
    settings.setAutoCommitOnCherryPick(myAutoCommitOnCherryPick.isSelected());
    settings.setWarnAboutCrlf(myWarnAboutCrlf.isSelected());
    settings.setWarnAboutDetachedHead(myWarnAboutDetachedHead.isSelected());
    settings.setForcePushAllowed(myEnableForcePush.isSelected());
    settings.setUpdateType((UpdateMethod)myUpdateMethodComboBox.getSelectedItem());
    sharedSettings.setForcePushProhibitedPatters(getProtectedBranchesPatterns());
  }

  @NotNull
  private List<String> getProtectedBranchesPatterns() {
    return ParametersListUtil.COLON_LINE_PARSER.fun(myProtectedBranchesButton.getText());
  }

  private void createUIComponents() {
    myProtectedBranchesButton = new TextFieldWithBrowseButton.NoPathCompletion(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Messages.showTextAreaDialog(myProtectedBranchesButton.getTextField(), "Protected Branches", "Git.Force.Push.Protected.Branches",
                                    ParametersListUtil.COLON_LINE_PARSER, ParametersListUtil.COLON_LINE_JOINER);
      }
    });
    myProtectedBranchesButton.setButtonIcon(AllIcons.Actions.ShowViewer);
    myUpdateMethodComboBox = new ComboBox(new EnumComboBoxModel<>(UpdateMethod.class));
    myUpdateMethodComboBox.setRenderer(new ListCellRendererWrapper<UpdateMethod>() {
      @Override
      public void customize(JList list, UpdateMethod value, int index, boolean selected, boolean hasFocus) {
        setText(StringUtil.capitalize(StringUtil.toLowerCase(value.name().replace('_', ' '))));
      }
    });
  }
}
