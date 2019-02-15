// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.ObjectUtils;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI.PanelFactory;
import com.intellij.util.ui.UIUtil;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.containers.ContainerUtil.sorted;
import static java.awt.GridBagConstraints.*;

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
  private JCheckBox myUpdateBranchInfoCheckBox;
  private JFormattedTextField myBranchUpdateTimeField;
  private JPanel myBranchTimePanel;
  private JBLabel mySupportedBranchUpLabel;
  private JPanel myIncomingOutgoingSettingPanel;
  private JBCheckBox myPreviewPushOnCommitAndPush;
  private JBCheckBox myPreviewPushProtectedOnly;
  private JPanel myPreviewPushProtectedOnlyBorder;

  public GitVcsPanel(@NotNull Project project, @NotNull GitExecutableManager executableManager) {
    myProject = project;
    myExecutableManager = executableManager;
    initUIComponents();
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
    myUpdateBranchInfoCheckBox.addItemListener(e -> UIUtil.setEnabled(myBranchTimePanel, myUpdateBranchInfoCheckBox.isSelected(), true));
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
    myAutoCommitOnCherryPick.setSelected(projectSettings.isAutoCommitOnCherryPick());
    myAddCherryPickSuffix.setSelected(projectSettings.shouldAddSuffixToCherryPicksOfPublishedCommits());
    myWarnAboutCrlf.setSelected(projectSettings.warnAboutCrlf());
    myWarnAboutDetachedHead.setSelected(projectSettings.warnAboutDetachedHead());
    myUpdateMethodComboBox.setSelectedItem(projectSettings.getUpdateType());
    myProtectedBranchesField.setText(ParametersListUtil.COLON_LINE_JOINER.fun(sharedSettings.getForcePushProhibitedPatterns()));
    myUpdateBranchInfoCheckBox.setSelected(projectSettings.shouldUpdateBranchInfo());
    boolean branchInfoSupported = isBranchInfoSupported();
    myUpdateBranchInfoCheckBox.setEnabled(branchInfoSupported);
    UIUtil.setEnabled(myBranchTimePanel, myUpdateBranchInfoCheckBox.isSelected() && branchInfoSupported, true);
    updateSupportedBranchInfo();
    myBranchUpdateTimeField.setValue(projectSettings.getBranchInfoUpdateTime());
    myPreviewPushOnCommitAndPush.setSelected(projectSettings.shouldPreviewPushOnCommitAndPush());
    myPreviewPushProtectedOnly.setSelected(projectSettings.isPreviewPushProtectedOnly());
    updateEnabled();
  }

  private void updateSupportedBranchInfo() {
    boolean branchInfoSupported = isBranchInfoSupported();
    mySupportedBranchUpLabel.setVisible(!branchInfoSupported);
    mySupportedBranchUpLabel.setForeground(!branchInfoSupported && myUpdateBranchInfoCheckBox.isSelected()
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
            projectSettings.isAutoCommitOnCherryPick() != myAutoCommitOnCherryPick.isSelected() ||
            projectSettings.shouldAddSuffixToCherryPicksOfPublishedCommits() != myAddCherryPickSuffix.isSelected() ||
            projectSettings.warnAboutCrlf() != myWarnAboutCrlf.isSelected() ||
            projectSettings.warnAboutDetachedHead() != myWarnAboutDetachedHead.isSelected() ||
            projectSettings.shouldPreviewPushOnCommitAndPush() != myPreviewPushOnCommitAndPush.isSelected() ||
            projectSettings.isPreviewPushProtectedOnly() != myPreviewPushProtectedOnly.isSelected() ||
            projectSettings.getUpdateType() != myUpdateMethodComboBox.getModel().getSelectedItem() ||
            isUpdateBranchSettingsModified(projectSettings) ||
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
    projectSettings.setAutoCommitOnCherryPick(myAutoCommitOnCherryPick.isSelected());
    projectSettings.setAddSuffixToCherryPicks(myAddCherryPickSuffix.isSelected());
    projectSettings.setWarnAboutCrlf(myWarnAboutCrlf.isSelected());
    projectSettings.setWarnAboutDetachedHead(myWarnAboutDetachedHead.isSelected());
    projectSettings.setUpdateType((UpdateMethod)myUpdateMethodComboBox.getSelectedItem());
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
    boolean branchInfoSupported = isBranchInfoSupported();
    myUpdateBranchInfoCheckBox.setEnabled(branchInfoSupported);
    UIUtil.setEnabled(myBranchTimePanel, myUpdateBranchInfoCheckBox.isSelected() && branchInfoSupported, true);
    updateSupportedBranchInfo();
    if (isUpdateBranchSettingsModified(projectSettings)) {
      projectSettings.setBranchInfoUpdateTime((Integer)myBranchUpdateTimeField.getValue());
      projectSettings.setUpdateBranchInfo(myUpdateBranchInfoCheckBox.isSelected());
      GitBranchIncomingOutgoingManager incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(myProject);
      incomingOutgoingManager.stopScheduling();
      if (projectSettings.shouldUpdateBranchInfo()) {
        incomingOutgoingManager.startScheduling();
      }
    }
  }

  private boolean isUpdateBranchSettingsModified(@NotNull GitVcsSettings projectSettings) {
    return projectSettings.getBranchInfoUpdateTime() != (Integer)myBranchUpdateTimeField.getValue() ||
           projectSettings.shouldUpdateBranchInfo() != myUpdateBranchInfoCheckBox.isSelected();
  }


  @NotNull
  private List<String> getProtectedBranchesPatterns() {
    return ParametersListUtil.COLON_LINE_PARSER.fun(myProtectedBranchesField.getText());
  }


  private JPanel getGitPathPanel() {
    JPanel gitPathPanel = new JBPanel(new GridBagLayout());
    JPanel pathFieldPanel = new JBPanel(new GridBagLayout());
    JBTextField textField = new JBTextField();
    textField.getEmptyText().setText("Auto-detected: " + myExecutableManager.getDetectedExecutable());
    myGitField = new TextFieldWithBrowseButton(textField);
    pathFieldPanel.add(myGitField, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, WEST, HORIZONTAL,
                                                          JBUI.emptyInsets(), 0, 0));
    myTestButton = new JButton(GitBundle.message("git.vcs.config.test"));
    pathFieldPanel.add(myTestButton, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, WEST, NONE,
                                                            JBUI.insets(0, 10, 0, 0), 0, 0));

    myProjectGitPathCheckBox = new JBCheckBox(DvcsBundle.message("executable.path.project.override"));

    gitPathPanel.add(new JBLabel(GitBundle.message("git.vcs.config.path.label")), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, WEST, NONE,
                                                                                                         JBUI.insets(0, 7, 0, 0), 0, 0));
    gitPathPanel.add(pathFieldPanel, new GridBagConstraints(1, 0, REMAINDER, 1, 1.0, 0.0, WEST, HORIZONTAL,
                                                            JBUI.insets(0, 10, 0, 0), 0, 0));
    gitPathPanel.add(myProjectGitPathCheckBox, new GridBagConstraints(1, 1, REMAINDER, REMAINDER, 0.0, 0.0, WEST, NONE,
                                                                      JBUI.insets(5, 10, 0, 0), 0, 0));
    return gitPathPanel;
  }

  private void initIncomingOutgoingSettingPanel() {
    myIncomingOutgoingSettingPanel = new JBPanel(new GridBagLayout());
    JPanel myIncomingOutgoingSettingHelperPanel = new JBPanel(new BorderLayout());
    myUpdateBranchInfoCheckBox = new JBCheckBox("Mark branches that have incoming/outgoing commits in the Branches popup. ");
    myIncomingOutgoingSettingHelperPanel.add(myUpdateBranchInfoCheckBox, BorderLayout.WEST);

    myBranchTimePanel = new JBPanel(new BorderLayout());
    myBranchTimePanel.add(new JBLabel(" Refresh every "), BorderLayout.WEST);
    NumberFormatter numberFormatter = new NumberFormatter(NumberFormat.getIntegerInstance());
    numberFormatter.setMinimum(1);
    numberFormatter.setAllowsInvalid(true);
    myBranchUpdateTimeField = new JFormattedTextField(numberFormatter);
    myBranchUpdateTimeField.setColumns(4);
    myBranchTimePanel.add(myBranchUpdateTimeField, BorderLayout.CENTER);
    myBranchTimePanel.add(new JBLabel(" minutes "), BorderLayout.EAST);
    myIncomingOutgoingSettingHelperPanel.add(myBranchTimePanel, BorderLayout.CENTER);

    mySupportedBranchUpLabel = new JBLabel("Supported from Git 2.9+");
    mySupportedBranchUpLabel.setBorder(JBUI.Borders.emptyLeft(2));

    myIncomingOutgoingSettingPanel.add(myIncomingOutgoingSettingHelperPanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, WEST, NONE,
                                                                                                    JBUI.emptyInsets(), 0, 0));
    myIncomingOutgoingSettingPanel.add(mySupportedBranchUpLabel, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, WEST, NONE,
                                                                                        JBUI.insets(0, 20, 0, 0), 0, 0));
  }

  @NotNull
  private JPanel getUpdateMethodPanel() {
    JPanel updateMethodPanel = new JBPanel(new GridBagLayout());
    updateMethodPanel.add(new JBLabel("Update method:"), new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, WEST, NONE,
                                                                                JBUI.emptyInsets(), 0, 0));

    myUpdateMethodComboBox = new ComboBox<>(new EnumComboBoxModel<>(UpdateMethod.class));
    myUpdateMethodComboBox.setRenderer(new ListCellRendererWrapper<UpdateMethod>() {
      @Override
      public void customize(JList list, UpdateMethod value, int index, boolean selected, boolean hasFocus) {
        setText(StringUtil.capitalize(StringUtil.toLowerCase(value.name().replace('_', ' '))));
      }
    });
    updateMethodPanel.add(myUpdateMethodComboBox, new GridBagConstraints(1, 0, 1, 1, 1.0, 1.0, WEST, NONE,
                                                                         JBUI.insets(0, 40, 0, 0), 0, 0));
    return updateMethodPanel;
  }

  @NotNull
  private JPanel getProtectedBranchesPanel() {
    JPanel protectedBranchesPanel = new JBPanel(new GridBagLayout());
    myProtectedBranchesLabel = new JBLabel("Protected branches:");
    myProtectedBranchesField = new ExpandableTextField(ParametersListUtil.COLON_LINE_PARSER, ParametersListUtil.COLON_LINE_JOINER);

    protectedBranchesPanel.add(myProtectedBranchesLabel, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, WEST, NONE,
                                                                                JBUI.emptyInsets(), 0, 0));
    protectedBranchesPanel.add(myProtectedBranchesField, new GridBagConstraints(1, 0, 1, 1, 1.0, 1.0, WEST, HORIZONTAL,
                                                                                JBUI.insets(0, 10, 0, 0), 0, 0));
    return protectedBranchesPanel;
  }

  @NotNull
  private JPanel getMainSettingsPanel() {
    JPanel mainSettingsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = JBUI.insets(3, 0, 3, 0);
    c.gridx = 0;
    c.gridy = RELATIVE;
    c.gridwidth = REMAINDER;
    c.anchor = WEST;
    c.fill = HORIZONTAL;
    c.weightx = 1.0;

    mySyncControl = new JBCheckBox(DvcsBundle.message("sync.setting"));
    mySyncControl.setAlignmentX(Component.LEFT_ALIGNMENT);
    mainSettingsPanel.add(mySyncControl, c);

    myAutoCommitOnCherryPick = new JBCheckBox("Commit automatically on cherry-pick");
    mainSettingsPanel.add(myAutoCommitOnCherryPick, c);

    myAddCherryPickSuffix = new JBCheckBox("Add the 'cherry-picked from <hash>' suffix when picking commits pushed to protected branches");
    mainSettingsPanel.add(myAddCherryPickSuffix, c);

    myWarnAboutCrlf = new JBCheckBox("Warn if " + UIUtil.MNEMONIC + "CRLF line separators are about to be committed");
    mainSettingsPanel.add(myWarnAboutCrlf, c);

    myWarnAboutDetachedHead = new JBCheckBox("Warn when committing in detached HEAD or during rebase");
    mainSettingsPanel.add(myWarnAboutDetachedHead, c);

    initIncomingOutgoingSettingPanel();
    mainSettingsPanel.add(myIncomingOutgoingSettingPanel, c);

    c.insets = JBUI.insets(2, 7, 2, 0);
    mainSettingsPanel.add(getUpdateMethodPanel(), c);
    c.insets = JBUI.insets(2, 0, 2, 0);

    myAutoUpdateIfPushRejected = new JBCheckBox("Auto-update if " + UIUtil.MNEMONIC + "push of the current branch was rejected");
    mainSettingsPanel.add(myAutoUpdateIfPushRejected, c);

    myPreviewPushOnCommitAndPush = new JBCheckBox("Show Push dialog for Commit and Push");
    mainSettingsPanel.add(myPreviewPushOnCommitAndPush, c);

    myPreviewPushProtectedOnlyBorder = new JBPanel(new BorderLayout());
    myPreviewPushProtectedOnly = new JBCheckBox("Show Push dialog only when committing to protected branches");
    myPreviewPushProtectedOnlyBorder.add(myPreviewPushProtectedOnly);
    mainSettingsPanel.add(myPreviewPushProtectedOnlyBorder, c);

    c.insets = JBUI.insets(2, 7, 2, 0);
    mainSettingsPanel.add(getProtectedBranchesPanel(), c);
    c.insets = JBUI.insets(2, 0, 2, 0);

    return mainSettingsPanel;
  }

  private void initUIComponents() {
    myRootPanel = PanelFactory.grid()
      .add(PanelFactory.panel(getGitPathPanel()))
      .add(PanelFactory.panel(getMainSettingsPanel()))
      .createPanel();
  }
}
