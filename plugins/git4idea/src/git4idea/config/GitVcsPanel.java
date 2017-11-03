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
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurableUi;
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
import com.intellij.ui.components.fields.ExpandableTextField;
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
public class GitVcsPanel implements ConfigurableUi<GitVcsConfigurable.GitVcsSettingsHolder> {

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
  private JTextField myProtectedBranchesField;
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
      final GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
      mySyncControl.setVisible(repositoryManager != null && repositoryManager.moreThanOneRoot());
    }
    else {
      mySyncControl.setVisible(true);
    }
    mySyncControl.setToolTipText(DvcsBundle.message("sync.setting.description", "Git"));
    myProtectedBranchesLabel.setLabelFor(myProtectedBranchesField);
    myEnableForcePush.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        UIUtil.setEnabled(myProtectedBranchesField, myEnableForcePush.isSelected(), true);
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
                                             version.getPresentation()),
                               GitBundle.getString("find.git.success.title"));
    }
    else {
      Messages.showWarningDialog(myRootPanel, GitBundle
                                   .message("find.git.unsupported.message", version.getPresentation(), GitVersion.MIN.getPresentation()),
                                 GitBundle.getString("find.git.success.title"));
    }
  }

  private String getCurrentExecutablePath() {
    return myGitField.getText().trim();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myRootPanel;
  }

  @Override
  public void reset(@NotNull GitVcsConfigurable.GitVcsSettingsHolder settings) {
    GitVcsApplicationSettings applicationSettings = settings.getApplicationSettings();
    GitVcsSettings projectSettings = settings.getProjectSettings();
    GitSharedSettings sharedSettings = settings.getSharedSettings();

    myGitField.setText(applicationSettings.getPathToGit());
    mySSHExecutableComboBox.setSelectedItem(projectSettings.isIdeaSsh() ? IDEA_SSH : NATIVE_SSH);
    myAutoUpdateIfPushRejected.setSelected(projectSettings.autoUpdateIfPushRejected());
    mySyncControl.setSelected(projectSettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC);
    myAutoCommitOnCherryPick.setSelected(projectSettings.isAutoCommitOnCherryPick());
    myWarnAboutCrlf.setSelected(projectSettings.warnAboutCrlf());
    myWarnAboutDetachedHead.setSelected(projectSettings.warnAboutDetachedHead());
    myEnableForcePush.setSelected(projectSettings.isForcePushAllowed());
    myUpdateMethodComboBox.setSelectedItem(projectSettings.getUpdateType());
    myProtectedBranchesField.setText(ParametersListUtil.COLON_LINE_JOINER.fun(sharedSettings.getForcePushProhibitedPatterns()));
  }

  @Override
  public boolean isModified(@NotNull GitVcsConfigurable.GitVcsSettingsHolder settings) {
    GitVcsApplicationSettings applicationSettings = settings.getApplicationSettings();
    GitVcsSettings projectSettings = settings.getProjectSettings();
    GitSharedSettings sharedSettings = settings.getSharedSettings();

    return !applicationSettings.getPathToGit().equals(getCurrentExecutablePath()) ||
           (projectSettings.isIdeaSsh() != IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem())) ||
           !projectSettings.autoUpdateIfPushRejected() == myAutoUpdateIfPushRejected.isSelected() ||
           ((projectSettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC) != mySyncControl.isSelected() ||
            projectSettings.isAutoCommitOnCherryPick() != myAutoCommitOnCherryPick.isSelected() ||
            projectSettings.warnAboutCrlf() != myWarnAboutCrlf.isSelected() ||
            projectSettings.warnAboutDetachedHead() != myWarnAboutDetachedHead.isSelected() ||
            projectSettings.isForcePushAllowed() != myEnableForcePush.isSelected() ||
            projectSettings.getUpdateType() != myUpdateMethodComboBox.getModel().getSelectedItem() ||
            !ContainerUtil.sorted(sharedSettings.getForcePushProhibitedPatterns()).equals(
              ContainerUtil.sorted(getProtectedBranchesPatterns())));
  }

  @Override
  public void apply(@NotNull GitVcsConfigurable.GitVcsSettingsHolder settings) {
    GitVcsApplicationSettings applicationSettings = settings.getApplicationSettings();
    GitVcsSettings projectSettings = settings.getProjectSettings();
    GitSharedSettings sharedSettings = settings.getSharedSettings();

    applicationSettings.setPathToGit(getCurrentExecutablePath());
    myVcs.checkVersion();
    applicationSettings.setIdeaSsh(IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem()) ?
                                   GitVcsApplicationSettings.SshExecutable.IDEA_SSH :
                                   GitVcsApplicationSettings.SshExecutable.NATIVE_SSH);
    projectSettings.setAutoUpdateIfPushRejected(myAutoUpdateIfPushRejected.isSelected());

    projectSettings.setSyncSetting(mySyncControl.isSelected() ? DvcsSyncSettings.Value.SYNC : DvcsSyncSettings.Value.DONT_SYNC);
    projectSettings.setAutoCommitOnCherryPick(myAutoCommitOnCherryPick.isSelected());
    projectSettings.setWarnAboutCrlf(myWarnAboutCrlf.isSelected());
    projectSettings.setWarnAboutDetachedHead(myWarnAboutDetachedHead.isSelected());
    projectSettings.setForcePushAllowed(myEnableForcePush.isSelected());
    projectSettings.setUpdateType((UpdateMethod)myUpdateMethodComboBox.getSelectedItem());
    sharedSettings.setForcePushProhibitedPatters(getProtectedBranchesPatterns());
  }

  @NotNull
  private List<String> getProtectedBranchesPatterns() {
    return ParametersListUtil.COLON_LINE_PARSER.fun(myProtectedBranchesField.getText());
  }

  private void createUIComponents() {
    myProtectedBranchesField = new ExpandableTextField(ParametersListUtil.COLON_LINE_PARSER, ParametersListUtil.COLON_LINE_JOINER);
    myUpdateMethodComboBox = new ComboBox(new EnumComboBoxModel<>(UpdateMethod.class));
    myUpdateMethodComboBox.setRenderer(new ListCellRendererWrapper<UpdateMethod>() {
      @Override
      public void customize(JList list, UpdateMethod value, int index, boolean selected, boolean hasFocus) {
        setText(StringUtil.capitalize(StringUtil.toLowerCase(value.name().replace('_', ' '))));
      }
    });
  }
}
