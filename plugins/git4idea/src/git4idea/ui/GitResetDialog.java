// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

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
  private ComboBox<ResetMode> myResetTypeComboBox;
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
    setTitle(GitBundle.message("reset.title"));
    setOKButtonText(GitBundle.message("git.reset.button"));
    myResetTypeComboBox.addItem(ResetMode.MIXED);
    myResetTypeComboBox.addItem(ResetMode.SOFT);
    myResetTypeComboBox.addItem(ResetMode.HARD);
    myResetTypeComboBox.setSelectedItem(ResetMode.MIXED);
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
      setErrorText(GitBundle.message("reset.commit.invalid"));
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
    ResetMode type = myResetTypeComboBox.getItem();
    if (type == ResetMode.SOFT) {
      handler.addParameters("--soft");
    }
    else if (type == ResetMode.HARD) {
      handler.addParameters("--hard");
    }
    else if (type == ResetMode.MIXED) {
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

  private enum ResetMode {
    SOFT("git.reset.mode.soft"),
    MIXED("git.reset.mode.mixed"),
    HARD("git.reset.mode.hard");

    private final String myId;

    ResetMode(@NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) String id) {
      myId = id;
    }

    @Override
    public String toString() {
      return GitBundle.message(myId);
    }
  }
}
