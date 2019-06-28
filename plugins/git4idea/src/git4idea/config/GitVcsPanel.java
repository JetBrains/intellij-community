// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.ObjectUtils;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.containers.ContainerUtil.sorted;
import static java.util.Objects.requireNonNull;

/**
 * Git VCS configuration panel
 */
public class GitVcsPanel implements ConfigurableUi<GitVcsConfigurable.GitVcsSettingsHolder> {

  @NotNull private final Project myProject;
  @NotNull private final GitExecutableManager myExecutableManager;
  private String myApplicationGitPath;
  private volatile boolean versionCheckRequested = false;

  private JButton myTestButton; // Test git executable
  private JComponent myRootPanel;
  private TextFieldWithBrowseButton myGitField;
  private JBCheckBox myProjectGitPathCheckBox;
  private JCheckBox myAutoUpdateIfPushRejected;
  private JBCheckBox mySyncControl;
  private JCheckBox myAutoCommitOnCherryPick;
  private JCheckBox myAddCherryPickSuffix;
  private JBCheckBox myWarnAboutCrlf;
  private JCheckBox myWarnAboutDetachedHead;
  private JTextField myProtectedBranchesField;
  private JBLabel myProtectedBranchesLabel;
  private JComboBox<UpdateMethod> myUpdateMethodComboBox;
  private JBLabel mySupportedBranchUpLabel;
  private JBCheckBox myPreviewPushOnCommitAndPush;
  private JBCheckBox myPreviewPushProtectedOnly;
  private JPanel myPreviewPushProtectedOnlyBorder;
  private JComboBox<GitIncomingCheckStrategy> myIncomingStrategyComboBox;
  private JPanel myCheckIncomingPanel;
  // TODO: add tooltip: Do not use credential helpers when using git from IDE. Only works with Git version >=2.9.0
  private JBCheckBox myUseCredHelperCheckbox;


  public GitVcsPanel(@NotNull Project project, @NotNull GitExecutableManager executableManager) {
    myProject = project;
    myExecutableManager = executableManager;
    myTestButton.addActionListener(e -> testExecutable());
    myGitField.addBrowseFolderListener(GitBundle.getString("find.git.title"), GitBundle.getString("find.git.description"), project,
                                       FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    myProjectGitPathCheckBox.addActionListener(e -> handleProjectOverrideStateChanged());
    if (!project.isDefault()) {
      final GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
      mySyncControl.setVisible(repositoryManager.moreThanOneRoot());
    }
    else {
      mySyncControl.setVisible(true);
    }
    mySyncControl.setToolTipText(DvcsBundle.message("sync.setting.description", "Git"));
    myProtectedBranchesLabel.setLabelFor(myProtectedBranchesField);
    myPreviewPushOnCommitAndPush.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateEnabled();
      }
    });
    Insets insets = myPreviewPushProtectedOnly.getBorder().getBorderInsets(myPreviewPushProtectedOnly);
    myPreviewPushProtectedOnlyBorder.setBorder(JBUI.Borders.emptyLeft(
      UIUtil.getCheckBoxTextHorizontalOffset(myPreviewPushOnCommitAndPush) - insets.left));
  }

  private void updateEnabled() {
    myPreviewPushProtectedOnly.setEnabled(myPreviewPushOnCommitAndPush.isSelected());
  }

  private void testExecutable() {
    String pathToGit = ObjectUtils.notNull(getCurrentExecutablePath(), myExecutableManager.getDetectedExecutable());
    new Task.Modal(myProject, GitBundle.getString("git.executable.version.progress.title"), true) {
      private GitVersion myVersion;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myExecutableManager.dropVersionCache(pathToGit);
        myVersion = myExecutableManager.identifyVersion(pathToGit);
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        GitExecutableProblemsNotifier.showExecutionErrorDialog(error, myProject);
      }

      @Override
      public void onSuccess() {
        if (myVersion.isSupported()) {
          Messages
            .showInfoMessage(myRootPanel,
                             GitBundle.message("git.executable.version.is", myVersion.getPresentation()),
                             GitBundle.getString("git.executable.version.success.title"));
        }
        else {
          GitExecutableProblemsNotifier.showUnsupportedVersionDialog(myVersion, myProject);
        }
      }
    }.queue();
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
    myProjectGitPathCheckBox.setSelected(projectSettingsPathToGit != null);
    myAutoUpdateIfPushRejected.setSelected(projectSettings.autoUpdateIfPushRejected());
    mySyncControl.setSelected(projectSettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC);
    myAutoCommitOnCherryPick.setSelected(applicationSettings.isAutoCommitOnCherryPick());
    myUseCredHelperCheckbox.setSelected(applicationSettings.isUseCredentialHelper());
    myAddCherryPickSuffix.setSelected(projectSettings.shouldAddSuffixToCherryPicksOfPublishedCommits());
    myWarnAboutCrlf.setSelected(projectSettings.warnAboutCrlf());
    myWarnAboutDetachedHead.setSelected(projectSettings.warnAboutDetachedHead());
    myUpdateMethodComboBox.setSelectedItem(projectSettings.getUpdateMethod());
    myProtectedBranchesField.setText(ParametersListUtil.COLON_LINE_JOINER.fun(sharedSettings.getForcePushProhibitedPatterns()));
    myIncomingStrategyComboBox.setSelectedItem(projectSettings.getIncomingCheckStrategy());
    updateBranchInfoPanel();
    myPreviewPushOnCommitAndPush.setSelected(projectSettings.shouldPreviewPushOnCommitAndPush());
    myPreviewPushProtectedOnly.setSelected(projectSettings.isPreviewPushProtectedOnly());
    updateEnabled();
  }

  private void updateBranchInfoPanel() {
    UIUtil.setEnabled(myCheckIncomingPanel, Registry.is("git.update.incoming.outgoing.info") && isBranchInfoSupported(), true);
    updateBranchSupportedHint();
  }

  private void updateBranchSupportedHint() {
    boolean branchInfoSupported = isBranchInfoSupported();
    mySupportedBranchUpLabel.setVisible(!branchInfoSupported);
    mySupportedBranchUpLabel
      .setForeground(!branchInfoSupported && myIncomingStrategyComboBox.getSelectedItem() != GitIncomingCheckStrategy.Never
                     ? DialogWrapper.ERROR_FOREGROUND_COLOR
                     : UIUtil.getContextHelpForeground());
  }

  private boolean isBranchInfoSupported() {
    return GitVersionSpecialty.INCOMING_OUTGOING_BRANCH_INFO.existsIn(myProject);
  }

  @Override
  public boolean isModified(@NotNull GitVcsConfigurable.GitVcsSettingsHolder settings) {
    GitVcsApplicationSettings applicationSettings = settings.getApplicationSettings();
    GitVcsSettings projectSettings = settings.getProjectSettings();
    GitSharedSettings sharedSettings = settings.getSharedSettings();

    return isGitPathModified(applicationSettings, projectSettings) ||
           !projectSettings.autoUpdateIfPushRejected() == myAutoUpdateIfPushRejected.isSelected() ||
           ((projectSettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC) != mySyncControl.isSelected() ||
            applicationSettings.isAutoCommitOnCherryPick() != myAutoCommitOnCherryPick.isSelected() ||
            applicationSettings.isUseCredentialHelper() != myUseCredHelperCheckbox.isSelected() ||
            projectSettings.shouldAddSuffixToCherryPicksOfPublishedCommits() != myAddCherryPickSuffix.isSelected() ||
            projectSettings.warnAboutCrlf() != myWarnAboutCrlf.isSelected() ||
            projectSettings.warnAboutDetachedHead() != myWarnAboutDetachedHead.isSelected() ||
            projectSettings.shouldPreviewPushOnCommitAndPush() != myPreviewPushOnCommitAndPush.isSelected() ||
            projectSettings.isPreviewPushProtectedOnly() != myPreviewPushProtectedOnly.isSelected() ||
            projectSettings.getUpdateMethod() != myUpdateMethodComboBox.getModel().getSelectedItem() ||
            projectSettings.getIncomingCheckStrategy() != myIncomingStrategyComboBox.getSelectedItem() ||
            !sorted(sharedSettings.getForcePushProhibitedPatterns()).equals(sorted(getProtectedBranchesPatterns())));
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

    projectSettings.setAutoUpdateIfPushRejected(myAutoUpdateIfPushRejected.isSelected());
    projectSettings.setSyncSetting(mySyncControl.isSelected() ? DvcsSyncSettings.Value.SYNC : DvcsSyncSettings.Value.DONT_SYNC);
    applicationSettings.setAutoCommitOnCherryPick(myAutoCommitOnCherryPick.isSelected());
    applicationSettings.setUseCredentialHelper(myUseCredHelperCheckbox.isSelected());
    projectSettings.setAddSuffixToCherryPicks(myAddCherryPickSuffix.isSelected());
    projectSettings.setWarnAboutCrlf(myWarnAboutCrlf.isSelected());
    projectSettings.setWarnAboutDetachedHead(myWarnAboutDetachedHead.isSelected());
    projectSettings.setUpdateMethod((UpdateMethod)myUpdateMethodComboBox.getSelectedItem());
    projectSettings.setPreviewPushOnCommitAndPush(myPreviewPushOnCommitAndPush.isSelected());
    projectSettings.setPreviewPushProtectedOnly(myPreviewPushProtectedOnly.isSelected());

    sharedSettings.setForcePushProhibitedPatters(getProtectedBranchesPatterns());
    applyBranchUpdateInfo(projectSettings);
    validateExecutableOnceAfterClose();
  }

  /**
   * Special method to check executable after it has been changed through settings
   */
  public void validateExecutableOnceAfterClose() {
    if (!versionCheckRequested) {
      ApplicationManager.getApplication().invokeLater(() -> {
        new Task.Backgroundable(myProject, GitBundle.getString("git.executable.version.progress.title"), true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            myExecutableManager.testGitExecutableVersionValid(myProject);
          }
        }.queue();
        versionCheckRequested = false;
      }, ModalityState.NON_MODAL);
      versionCheckRequested = true;
    }
  }

  private void applyBranchUpdateInfo(@NotNull GitVcsSettings projectSettings) {
    if (!Registry.is("git.update.incoming.outgoing.info")) return;
    updateBranchInfoPanel();
    GitIncomingCheckStrategy selectedStrategy = requireNonNull((GitIncomingCheckStrategy)myIncomingStrategyComboBox.getSelectedItem());
    if (projectSettings.getIncomingCheckStrategy() != selectedStrategy) {
      projectSettings.setIncomingCheckStrategy(selectedStrategy);
      GitBranchIncomingOutgoingManager.getInstance(myProject).updateIncomingScheduling();
    }
  }

  @NotNull
  private List<String> getProtectedBranchesPatterns() {
    return ParametersListUtil.COLON_LINE_PARSER.fun(myProtectedBranchesField.getText());
  }

  private void createUIComponents() {
    JBTextField textField = new JBTextField();
    textField.getEmptyText().setText("Auto-detected: " + myExecutableManager.getDetectedExecutable());
    myGitField = new TextFieldWithBrowseButton(textField);
    myProtectedBranchesField = new ExpandableTextField(ParametersListUtil.COLON_LINE_PARSER, ParametersListUtil.COLON_LINE_JOINER);
    myUpdateMethodComboBox = new ComboBox<>(new EnumComboBoxModel<>(UpdateMethod.class));
    myUpdateMethodComboBox.setRenderer(SimpleListCellRenderer.create("", value ->
      StringUtil.capitalize(StringUtil.toLowerCase(value.name().replace('_', ' ')))));
    myIncomingStrategyComboBox = new ComboBox<>(new EnumComboBoxModel<>(GitIncomingCheckStrategy.class));
    mySupportedBranchUpLabel = new JBLabel("Supported from Git 2.9+");
    mySupportedBranchUpLabel.setBorder(JBUI.Borders.emptyLeft(2));
  }
}
