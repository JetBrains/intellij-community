// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * The dialog for the "git reset" operation
 */
public class GitResetDialog extends DialogWrapper {
  /**
   * Git root selector
   */
  private JComboBox myGitRootComboBox;
  /**
   * The label for the current branch
   */
  private JLabel myCurrentBranchLabel;
  /**
   * The selector for reset type
   */
  private JComboBox myResetTypeComboBox;
  /**
   * The text field that contains commit expressions
   */
  private JTextField myCommitTextField;
  /**
   * The validate button
   */
  private JButton myValidateButton;
  /**
   * The root panel for the dialog
   */
  private JPanel myPanel;

  /**
   * The project
   */
  private final Project myProject;
  /**
   * The validator for commit text
   */
  private final GitReferenceValidator myGitReferenceValidator;

  /**
   * A constructor
   *
   * @param project     the project
   * @param roots       the list of the roots
   * @param defaultRoot the default root to select
   */
  public GitResetDialog(final Project project, final List<VirtualFile> roots, final VirtualFile defaultRoot) {
    super(project, true);
    myProject = project;
    setTitle(GitBundle.getString("reset.title"));
    setOKButtonText(GitBundle.message("git.reset.button"));
    myResetTypeComboBox.addItem(getMixed());
    myResetTypeComboBox.addItem(getSoft());
    myResetTypeComboBox.addItem(getHard());
    myResetTypeComboBox.setSelectedItem(getMixed());
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRootComboBox, myCurrentBranchLabel);
    myGitReferenceValidator = new GitReferenceValidator(myProject, myGitRootComboBox, myCommitTextField, myValidateButton,
                                                        () -> validateFields());
    init();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCommitTextField;
  }

  /**
   * Validate
   */
  void validateFields() {
    if (myGitReferenceValidator.isInvalid()) {
      setErrorText(GitBundle.getString("reset.commit.invalid"));
      setOKActionEnabled(false);
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  /**
   * @return the handler for reset operation
   */
  public GitLineHandler handler() {
    GitLineHandler handler = new GitLineHandler(myProject, getGitRoot(), GitCommand.RESET);
    String type = (String)myResetTypeComboBox.getSelectedItem();
    if (getSoft().equals(type)) {
      handler.addParameters("--soft");
    }
    else if (getHard().equals(type)) {
      handler.addParameters("--hard");
    }
    else if (getMixed().equals(type)) {
      handler.addParameters("--mixed");
    }
    final String commit = myCommitTextField.getText().trim();
    if (commit.length() != 0) {
      handler.addParameters(commit);
    }
    handler.endOptions();
    return handler;
  }

  /**
   * @return the selected git root
   */
  public VirtualFile getGitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getHelpId() {
    return "gitResetHead";
  }

  /**
   * The --soft reset type
   */
  static String getSoft() {
    return GitBundle.getString("git.reset.mode.soft");
  }

  /**
   * The --mixed reset type
   */
  static String getMixed() {
    return GitBundle.getString("git.reset.mode.mixed");
  }

  /**
   * The --hard reset type
   */
  static String getHard() {
    return GitBundle.getString("git.reset.mode.hard");
  }
}
