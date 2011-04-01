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
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
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
  private JButton myTestButton; // Test git executable
  private JComponent myRootPanel;
  private TextFieldWithBrowseButton myGitField;
  private JComboBox mySSHExecutableComboBox; // Type of SSH executable to use
  private JComboBox myConvertTextFilesComboBox; // The conversion policy
  private final Project myProject;
  private final GitVcsApplicationSettings myAppSettings;
  private final GitVcsSettings myProjectSettings;
  private static final String IDEA_SSH = ApplicationNamesInfo.getInstance().getProductName() + " " + GitBundle.getString("git.vcs.config.ssh.mode.idea"); // IDEA ssh value
  private static final String NATIVE_SSH = GitBundle.getString("git.vcs.config.ssh.mode.native"); // Native SSH value
  private static final String CRLF_CONVERT_TO_PROJECT = GitBundle.getString("git.vcs.config.convert.project");
  private static final String CRLF_DO_NOT_CONVERT = GitBundle.getString("git.vcs.config.convert.do.not.convert");
  private static final String CRLF_ASK = GitBundle.getString("git.vcs.config.convert.ask");
  private GitVcs myVcs;

  /**
   * The constructor
   *
   * @param project the context project
   */
  public GitVcsPanel(@NotNull Project project) {
    myVcs = GitVcs.getInstance(project);
    myAppSettings = GitVcsApplicationSettings.getInstance();
    myProjectSettings = GitVcsSettings.getInstance(project);
    myProject = project;
    mySSHExecutableComboBox.addItem(IDEA_SSH);
    mySSHExecutableComboBox.addItem(NATIVE_SSH);
    mySSHExecutableComboBox.setSelectedItem(GitVcsSettings.isDefaultIdeaSsh() ? IDEA_SSH : NATIVE_SSH);
    mySSHExecutableComboBox
      .setToolTipText(GitBundle.message("git.vcs.config.ssh.mode.tooltip", ApplicationNamesInfo.getInstance().getFullProductName()));
    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        testConnection();
      }
    });
    myConvertTextFilesComboBox.addItem(CRLF_DO_NOT_CONVERT);
    myConvertTextFilesComboBox.addItem(CRLF_CONVERT_TO_PROJECT);
    myConvertTextFilesComboBox.addItem(CRLF_ASK);
    myConvertTextFilesComboBox.setSelectedItem(CRLF_ASK);
    myGitField.addBrowseFolderListener(GitBundle.getString("find.git.title"), GitBundle.getString("find.git.description"), project,
                                       new FileChooserDescriptor(true, false, false, false, false, false));
  }

  /**
   * Test availability of the connection
   */
  private void testConnection() {
    final String executable = myGitField.getText();
    if (myAppSettings != null) {
      myAppSettings.setPathToGit(executable);
    }
    final GitVersion version;
    try {
      version = GitVersion.identifyVersion(executable);
    } catch (Exception e) {
      Messages.showErrorDialog(myProject, e.getMessage(), GitBundle.getString("find.git.error.title"));
      return;
    }

    if (version.isSupported()) {
      Messages.showInfoMessage(myProject,
                               String.format("<html>%s<br>Git version is %s</html>", GitBundle.getString("find.git.success.title"),
                                             version.toString()),
                               GitBundle.getString("find.git.success.title"));
    } else {
      Messages.showWarningDialog(myProject, GitBundle.message("find.git.unsupported.message", version.toString(), GitVersion.MIN),
                                 GitBundle.getString("find.git.success.title"));
    }
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
    myConvertTextFilesComboBox.setSelectedItem(crlfPolicyItem(settings));
  }

  /**
   * Get crlf policy item from settings
   *
   * @param settings the settings object
   * @return the item in crlf combobox
   */
  static private String crlfPolicyItem(GitVcsSettings settings) {
    String crlf;
    switch (settings.getLineSeparatorsConversion()) {
      case NONE:
        crlf = CRLF_DO_NOT_CONVERT;
        break;
      case CONVERT:
        crlf = CRLF_CONVERT_TO_PROJECT;
        break;
      case ASK:
        crlf = CRLF_ASK;
        break;
      default:
        assert false : "Unknown crlf policy: " + settings.getLineSeparatorsConversion();
        crlf = null;
    }
    return crlf;
  }

  /**
   * Check if fields has been modified with respect to settings object
   *
   * @param settings the settings to load
   */
  public boolean isModified(@NotNull GitVcsSettings settings) {
    return !settings.getAppSettings().getPathToGit().equals(myGitField.getText()) ||
           (settings.isIdeaSsh() != IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem())) ||
           !crlfPolicyItem(settings).equals(myConvertTextFilesComboBox.getSelectedItem());
  }

  /**
   * Save configuration panel state into settings object
   *
   * @param settings the settings object
   */
  public void save(@NotNull GitVcsSettings settings) {
    settings.getAppSettings().setPathToGit(myGitField.getText());
    myVcs.checkVersion();
    settings.setIdeaSsh(IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem()));
    Object policyItem = myConvertTextFilesComboBox.getSelectedItem();
    GitVcsSettings.ConversionPolicy conversionPolicy;
    if (CRLF_DO_NOT_CONVERT.equals(policyItem)) {
      conversionPolicy = GitVcsSettings.ConversionPolicy.NONE;
    } else if (CRLF_CONVERT_TO_PROJECT.equals(policyItem)) {
      conversionPolicy = GitVcsSettings.ConversionPolicy.CONVERT;
    } else if (CRLF_ASK.equals(policyItem)) {
      conversionPolicy = GitVcsSettings.ConversionPolicy.ASK;
    }
    else {
      throw new IllegalStateException("Unknown selected CRLF policy: " + policyItem);
    }
    settings.setLineSeparatorsConversion(conversionPolicy);
  }
}
