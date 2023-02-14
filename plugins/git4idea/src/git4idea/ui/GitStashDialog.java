// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitStashUsageCollector;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.stash.GitStashUtils;
import git4idea.util.GitUIUtil;

import javax.swing.*;
import java.util.List;

/**
 * The git stash dialog.
 */
public class GitStashDialog extends DialogWrapper {
  private JComboBox myGitRootComboBox; // Git root selector
  private JPanel myRootPanel;
  private JLabel myCurrentBranch;
  private JTextField myMessageTextField;
  private JCheckBox myKeepIndexCheckBox; // --keep-index
  private final Project myProject;

  /**
   * A constructor
   *
   * @param project     the project
   * @param roots       the list of Git roots
   * @param defaultRoot the default root to select
   */
  public GitStashDialog(final Project project, final List<VirtualFile> roots, final VirtualFile defaultRoot) {
    super(project, true);
    myProject = project;
    setTitle(GitBundle.message("stash.title"));
    setOKButtonText(GitBundle.message("stash.button"));
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRootComboBox, myCurrentBranch);
    init();
  }

  public GitLineHandler handler() {
    return GitStashUtils.createStashHandler(myProject, getGitRoot(), myKeepIndexCheckBox.isSelected(), myMessageTextField.getText());
  }

  public void logUsage() {
    GitStashUsageCollector.logStashPushDialog(!StringUtil.isEmpty(myMessageTextField.getText()), myKeepIndexCheckBox.isSelected());
  }

  /**
   * @return the selected git root
   */
  public VirtualFile getGitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.Stash";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myMessageTextField;
  }
}
