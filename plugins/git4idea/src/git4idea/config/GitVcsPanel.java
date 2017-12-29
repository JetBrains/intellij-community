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
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

/**
 * Git VCS configuration panel
 */
public class GitVcsPanel implements ConfigurableUi<GitVcsConfigurable.GitVcsSettingsHolder> {

  private static final String IDEA_SSH = GitBundle.getString("git.vcs.config.ssh.mode.idea"); // IDEA ssh value
  private static final String NATIVE_SSH = GitBundle.getString("git.vcs.config.ssh.mode.native"); // Native SSH value

  @NotNull private final Project myProject;
  private String myDetectedGitPath;
  private String myApplicationGitPath;

  private JButton myTestButton; // Test git executable
  private JComponent myRootPanel;
  private TextFieldWithBrowseButton myGitField;
  private JBCheckBox myProjectGitPathCheckBox;
  private JComboBox mySSHExecutableComboBox; // Type of SSH executable to use
  private JCheckBox myAutoUpdateIfPushRejected;
  private JBCheckBox mySyncControl;
  private JCheckBox myAutoCommitOnCherryPick;
  private JBCheckBox myWarnAboutCrlf;
  private JCheckBox myWarnAboutDetachedHead;
  private JTextField myProtectedBranchesField;
  private JBLabel myProtectedBranchesLabel;
  private JComboBox myUpdateMethodComboBox;

  public GitVcsPanel(@NotNull Project project) {
    myProject = project;
    mySSHExecutableComboBox.addItem(IDEA_SSH);
    mySSHExecutableComboBox.addItem(NATIVE_SSH);
    mySSHExecutableComboBox.setSelectedItem(IDEA_SSH);
    mySSHExecutableComboBox
      .setToolTipText(GitBundle.message("git.vcs.config.ssh.mode.tooltip", ApplicationNamesInfo.getInstance().getFullProductName()));
    myTestButton.addActionListener(e -> testExecutable());
    myGitField.addBrowseFolderListener(GitBundle.getString("find.git.title"), GitBundle.getString("find.git.description"), project,
                                       FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    myProjectGitPathCheckBox.addActionListener(e -> handleProjectOverrideStateChanged());
    if (!project.isDefault()) {
      final GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
      mySyncControl.setVisible(repositoryManager != null && repositoryManager.moreThanOneRoot());
    }
    else {
      mySyncControl.setVisible(true);
    }
    mySyncControl.setToolTipText(DvcsBundle.message("sync.setting.description", "Git"));
    myProtectedBranchesLabel.setLabelFor(myProtectedBranchesField);
  }

  private void testExecutable() {
    GitVersion version;
    String executable = ObjectUtils.notNull(getCurrentExecutablePath(), myDetectedGitPath);
    try {
      version = ProgressManager.getInstance().runProcessWithProgressSynchronously(new ThrowableComputable<GitVersion, Exception>() {
        @Override
        public GitVersion compute() throws Exception {
          return GitVersion.identifyVersion(executable);
        }
      }, "Testing Git Executable...", true, myProject);
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

  private void handleProjectOverrideStateChanged() {
    if (!myProjectGitPathCheckBox.isSelected()
        && !Objects.equals(getCurrentExecutablePath(), myApplicationGitPath)) {

      switch (Messages.showYesNoCancelDialog(myRootPanel,
                                             VcsBundle.getString("executable.project.override.reset.message"),
                                             VcsBundle.getString("executable.project.override.reset.title"),
                                             VcsBundle.getString("executable.project.override.reset.globalize"),
                                             VcsBundle.getString("executable.project.override.reset.revert"),
                                             Messages.CANCEL_BUTTON,
                                             null)) {
        case Messages.NO:
          myGitField.setText(myApplicationGitPath);
          break;
        case Messages.CANCEL:
          myProjectGitPathCheckBox.setSelected(true);
          break;
      }
    }
  }

  @Nullable
  private String getCurrentExecutablePath() {
    return StringUtil.nullize(myGitField.getText().trim());
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

    myApplicationGitPath = applicationSettings.getSavedPathToGit();
    String projectSettingsPathToGit = projectSettings.getPathToGit();
    myGitField.setText(ObjectUtils.coalesce(projectSettingsPathToGit, myApplicationGitPath));
    myDetectedGitPath = GitExecutableManager.getInstance().getDetectedExecutable();
    ((JBTextField)myGitField.getTextField()).getEmptyText().setText("Auto-detected: " + myDetectedGitPath);
    myProjectGitPathCheckBox.setSelected(projectSettingsPathToGit != null);
    mySSHExecutableComboBox.setSelectedItem(projectSettings.isIdeaSsh() ? IDEA_SSH : NATIVE_SSH);
    myAutoUpdateIfPushRejected.setSelected(projectSettings.autoUpdateIfPushRejected());
    mySyncControl.setSelected(projectSettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC);
    myAutoCommitOnCherryPick.setSelected(projectSettings.isAutoCommitOnCherryPick());
    myWarnAboutCrlf.setSelected(projectSettings.warnAboutCrlf());
    myWarnAboutDetachedHead.setSelected(projectSettings.warnAboutDetachedHead());
    myUpdateMethodComboBox.setSelectedItem(projectSettings.getUpdateType());
    myProtectedBranchesField.setText(ParametersListUtil.COLON_LINE_JOINER.fun(sharedSettings.getForcePushProhibitedPatterns()));
  }

  @Override
  public boolean isModified(@NotNull GitVcsConfigurable.GitVcsSettingsHolder settings) {
    GitVcsApplicationSettings applicationSettings = settings.getApplicationSettings();
    GitVcsSettings projectSettings = settings.getProjectSettings();
    GitSharedSettings sharedSettings = settings.getSharedSettings();

    return isGitPathModified(applicationSettings, projectSettings) ||
           (projectSettings.isIdeaSsh() != IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem())) ||
           !projectSettings.autoUpdateIfPushRejected() == myAutoUpdateIfPushRejected.isSelected() ||
           ((projectSettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC) != mySyncControl.isSelected() ||
            projectSettings.isAutoCommitOnCherryPick() != myAutoCommitOnCherryPick.isSelected() ||
            projectSettings.warnAboutCrlf() != myWarnAboutCrlf.isSelected() ||
            projectSettings.warnAboutDetachedHead() != myWarnAboutDetachedHead.isSelected() ||
            projectSettings.getUpdateType() != myUpdateMethodComboBox.getModel().getSelectedItem() ||
            !ContainerUtil.sorted(sharedSettings.getForcePushProhibitedPatterns()).equals(
              ContainerUtil.sorted(getProtectedBranchesPatterns())));
  }

  private boolean isGitPathModified(@NotNull GitVcsApplicationSettings applicationSettings, @NotNull GitVcsSettings projectSettings) {
    return myProjectGitPathCheckBox.isSelected()
           ? !Objects.equals(getCurrentExecutablePath(), projectSettings.getPathToGit())
           : !Objects.equals(getCurrentExecutablePath(), applicationSettings.getSavedPathToGit())
             || projectSettings.getPathToGit() != null;
  }

  @Override
  public void apply(@NotNull GitVcsConfigurable.GitVcsSettingsHolder settings) {
    GitVcsApplicationSettings applicationSettings = settings.getApplicationSettings();
    GitVcsSettings projectSettings = settings.getProjectSettings();
    GitSharedSettings sharedSettings = settings.getSharedSettings();

    if (myProjectGitPathCheckBox.isSelected()) {
      projectSettings.setPathToGit(getCurrentExecutablePath());
    }
    else {
      myApplicationGitPath = getCurrentExecutablePath();
      applicationSettings.setPathToGit(getCurrentExecutablePath());
      projectSettings.setPathToGit(null);
    }

    applicationSettings.setIdeaSsh(IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem()) ?
                                   GitVcsApplicationSettings.SshExecutable.IDEA_SSH :
                                   GitVcsApplicationSettings.SshExecutable.NATIVE_SSH);

    projectSettings.setAutoUpdateIfPushRejected(myAutoUpdateIfPushRejected.isSelected());
    projectSettings.setSyncSetting(mySyncControl.isSelected() ? DvcsSyncSettings.Value.SYNC : DvcsSyncSettings.Value.DONT_SYNC);
    projectSettings.setAutoCommitOnCherryPick(myAutoCommitOnCherryPick.isSelected());
    projectSettings.setWarnAboutCrlf(myWarnAboutCrlf.isSelected());
    projectSettings.setWarnAboutDetachedHead(myWarnAboutDetachedHead.isSelected());
    projectSettings.setUpdateType((UpdateMethod)myUpdateMethodComboBox.getSelectedItem());

    sharedSettings.setForcePushProhibitedPatters(getProtectedBranchesPatterns());

    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> GitVcs.getInstance(myProject).checkVersion(),
                                                                      "Testing Git Executable...", true, null,
                                                                      myRootPanel);
  }

  @NotNull
  private List<String> getProtectedBranchesPatterns() {
    return ParametersListUtil.COLON_LINE_PARSER.fun(myProtectedBranchesField.getText());
  }

  private void createUIComponents() {
    myGitField = new TextFieldWithBrowseButton(new JBTextField());
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
