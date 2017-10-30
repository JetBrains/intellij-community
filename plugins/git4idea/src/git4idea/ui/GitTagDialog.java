/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.util.GitUIUtil;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The tag dialog for the git
 */
public class GitTagDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(GitTagDialog.class);

  /**
   * Root panel
   */
  private JPanel myPanel;
  /**
   * Git root selector
   */
  private JComboBox myGitRootComboBox;
  /**
   * Current branch label
   */
  private JLabel myCurrentBranch;
  /**
   * Tag name
   */
  private JTextField myTagNameTextField;
  /**
   * Force tag creation checkbox
   */
  private JCheckBox myForceCheckBox;
  /**
   * Text area that contains tag message if non-empty
   */
  private JTextArea myMessageTextArea;
  /**
   * The name of commit to tag
   */
  private JTextField myCommitTextField;
  /**
   * The validate button
   */
  private JButton myValidateButton;
  /**
   * The validator for commit text field
   */
  private final GitReferenceValidator myCommitTextFieldValidator;
  /**
   * The current project
   */
  private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final VcsNotifier myNotifier;

  /**
   * Existing tags for the project
   */
  private final Set<String> myExistingTags = new HashSet<>();
  /**
   * Prefix for message file name
   */
  @NonNls private static final String MESSAGE_FILE_PREFIX = "git-tag-message-";
  /**
   * Suffix for message file name
   */
  @NonNls private static final String MESSAGE_FILE_SUFFIX = ".txt";
  /**
   * Encoding for the message file
   */
  @NonNls private static final String MESSAGE_FILE_ENCODING = CharsetToolkit.UTF8;

  /**
   * A constructor
   *
   * @param project     a project to select
   * @param roots       a git repository roots for the project
   * @param defaultRoot a guessed default root
   */
  public GitTagDialog(Project project, List<VirtualFile> roots, VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.getString("tag.title"));
    setOKButtonText(GitBundle.getString("tag.button"));
    myProject = project;
    myNotifier = VcsNotifier.getInstance(myProject);
    myGit = Git.getInstance();

    GitUIUtil.setupRootChooser(myProject, roots, defaultRoot, myGitRootComboBox, myCurrentBranch);
    myGitRootComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        fetchTags();
        validateFields();
      }
    });
    fetchTags();
    myTagNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateFields();
      }
    });
    myCommitTextFieldValidator = new GitReferenceValidator(project, myGitRootComboBox, myCommitTextField, myValidateButton,
                                                           () -> validateFields());
    myForceCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (myForceCheckBox.isEnabled()) {
          validateFields();
        }
      }
    });
    init();
    validateFields();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTagNameTextField;
  }

  /**
   * Perform tagging according to selected options
   */
  public void runAction() {
    final String message = myMessageTextArea.getText();
    final boolean hasMessage = message.trim().length() != 0;
    final File messageFile;
    if (hasMessage) {
      try {
        messageFile = FileUtil.createTempFile(MESSAGE_FILE_PREFIX, MESSAGE_FILE_SUFFIX);
        messageFile.deleteOnExit();
        Writer out = new OutputStreamWriter(new FileOutputStream(messageFile), MESSAGE_FILE_ENCODING);
        try {
          out.write(message);
        }
        finally {
          out.close();
        }
      }
      catch (IOException ex) {
        Messages.showErrorDialog(myProject, GitBundle.message("tag.error.creating.message.file.message", ex.toString()),
                                 GitBundle.getString("tag.error.creating.message.file.title"));
        return;
      }
    }
    else {
      messageFile = null;
    }
    try {
      GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.TAG);
      if (hasMessage) {
        h.addParameters("-a");
      }
      if (myForceCheckBox.isEnabled() && myForceCheckBox.isSelected()) {
        h.addParameters("-f");
      }
      if (hasMessage) {
        h.addParameters("-F", messageFile.getAbsolutePath());
      }
      h.addParameters(myTagNameTextField.getText());
      String object = myCommitTextField.getText().trim();
      if (object.length() != 0) {
        h.addParameters(object);
      }

      GitCommandResult result = myGit.runCommand(h);
      if (result.success()) {
        myNotifier.notifySuccess(myTagNameTextField.getText(),
                                 "Created tag " + myTagNameTextField.getText() + " successfully.");
      }
      else {
        myNotifier.notifyError("Couldn't Create Tag", result.getErrorOutputAsHtmlString());
      }

      GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(getGitRoot());
      if (repository != null) {
        repository.getRepositoryFiles().refresh();
      }
      else {
        LOG.error("No repository registered for root: " + getGitRoot());
      }
    }
    finally {
      if (messageFile != null) {
        //noinspection ResultOfMethodCallIgnored
        messageFile.delete();
      }
    }
  }

  /**
   * Validate dialog fields
   */
  private void validateFields() {
    String text = myTagNameTextField.getText();
    if (myExistingTags.contains(text)) {
      myForceCheckBox.setEnabled(true);
      if (!myForceCheckBox.isSelected()) {
        setErrorText(GitBundle.getString("tag.error.tag.exists"));
        setOKActionEnabled(false);
        return;
      }
    }
    else {
      myForceCheckBox.setEnabled(false);
      myForceCheckBox.setSelected(false);
    }
    if (myCommitTextFieldValidator.isInvalid()) {
      setErrorText(GitBundle.getString("tag.error.invalid.commit"));
      setOKActionEnabled(false);
      return;
    }
    if (text.length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  /**
   * Fetch tags
   */
  private void fetchTags() {
    myExistingTags.clear();
    GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.TAG);
    h.setSilent(true);
    GitCommandResult result = ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(() -> myGit.runCommand(h),
                                           GitBundle.getString("tag.getting.existing.tags"),
                                           false,
                                           myProject);
    if (!result.success()) {
      GitUIUtil.showOperationError(myProject, GitBundle.getString("tag.getting.existing.tags"), result.getErrorOutputAsJoinedString());
      throw new ProcessCanceledException();
    }
    for (StringScanner s = new StringScanner(result.getOutputAsJoinedString()); s.hasMoreData(); ) {
      String line = s.line();
      if (line.length() == 0) {
        continue;
      }
      myExistingTags.add(line);
    }
  }

  /**
   * @return the current git root
   */
  private VirtualFile getGitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
  }

  /**
   * {@inheritDoc}
   */
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
    return "reference.VersionControl.Git.TagFiles";
  }
}
