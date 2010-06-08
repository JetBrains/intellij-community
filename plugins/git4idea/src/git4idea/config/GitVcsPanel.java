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
   * The conversion policy
   */
  private JComboBox myConvertTextFilesComboBox;
  /**
   * The confirmation checkbox
   */
  private JCheckBox myAskBeforeConversionsCheckBox;
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
  private static final String IDEA_SSH =
    ApplicationNamesInfo.getInstance().getProductName() + " " + GitBundle.getString("git.vcs.config.ssh.mode.idea");
  /**
   * Native SSH value
   */
  private static final String NATIVE_SSH = GitBundle.getString("git.vcs.config.ssh.mode.native");
  /**
   * IDEA ssh value
   */
  private static final String CRLF_CONVERT_TO_PROJECT = GitBundle.getString("git.vcs.config.convert.project");
  /**
   * Native SSH value
   */
  private static final String CRLF_DO_NOT_CONVERT = GitBundle.getString("git.vcs.config.convert.do.not.convert");

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
    mySSHExecutableComboBox
      .setToolTipText(GitBundle.message("git.vcs.config.ssh.mode.tooltip", ApplicationNamesInfo.getInstance().getFullProductName()));
    myAskBeforeConversionsCheckBox.setSelected(mySettings.askBeforeLineSeparatorConversion());
    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        testConnection();
      }
    });
    myConvertTextFilesComboBox.addItem(CRLF_DO_NOT_CONVERT);
    myConvertTextFilesComboBox.addItem(CRLF_CONVERT_TO_PROJECT);
    myConvertTextFilesComboBox.setSelectedItem(CRLF_CONVERT_TO_PROJECT);
    myGitField.addBrowseFolderListener(GitBundle.getString("find.git.title"), GitBundle.getString("find.git.description"), project,
                                       new FileChooserDescriptor(true, false, false, false, false, false));
  }

  /**
   * Test availability of the connection
   */
  private void testConnection() {
    mySettings.setGitExecutable(myGitField.getText());
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
    myGitField.setText(settings.getGitExecutable());
    mySSHExecutableComboBox.setSelectedItem(settings.isIdeaSsh() ? IDEA_SSH : NATIVE_SSH);
    myAskBeforeConversionsCheckBox.setSelected(settings.askBeforeLineSeparatorConversion());
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
      case PROJECT_LINE_SEPARATORS:
        crlf = CRLF_CONVERT_TO_PROJECT;
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
    return !settings.getGitExecutable().equals(myGitField.getText()) ||
           (settings.isIdeaSsh() != IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem())) ||
           !crlfPolicyItem(settings).equals(myConvertTextFilesComboBox.getSelectedItem()) ||
           settings.askBeforeLineSeparatorConversion() != myAskBeforeConversionsCheckBox.isSelected();
  }

  /**
   * Save configuration panel state into settings object
   *
   * @param settings the settings object
   */
  public void save(@NotNull GitVcsSettings settings) {
    settings.setGitExecutable(myGitField.getText());
    settings.setIdeaSsh(IDEA_SSH.equals(mySSHExecutableComboBox.getSelectedItem()));
    Object policyItem = myConvertTextFilesComboBox.getSelectedItem();
    GitVcsSettings.ConversionPolicy conversionPolicy;
    if (CRLF_DO_NOT_CONVERT.equals(policyItem)) {
      conversionPolicy = GitVcsSettings.ConversionPolicy.NONE;
    }
    else if (CRLF_CONVERT_TO_PROJECT.equals(policyItem)) {
      conversionPolicy = GitVcsSettings.ConversionPolicy.PROJECT_LINE_SEPARATORS;
    }
    else {
      throw new IllegalStateException("Unknown selected CRLF policy: " + policyItem);
    }
    settings.setLineSeparatorsConversion(conversionPolicy);
    settings.setAskBeforeLineSeparatorConversion(myAskBeforeConversionsCheckBox.isSelected());
  }
}
