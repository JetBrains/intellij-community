// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.util.GitUIUtil;
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

import static git4idea.GitNotificationIdsHolder.TAG_NOT_CREATED;
import static git4idea.GitNotificationIdsHolder.TAG_CREATED;

public class GitTagDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(GitTagDialog.class);

  private JPanel myPanel;
  private JComboBox myGitRootComboBox;
  private JLabel myCurrentBranch;
  private JTextField myTagNameTextField;
  private JCheckBox myForceCheckBox;
  private JTextArea myMessageTextArea;
  private JTextField myCommitTextField;
  private JButton myValidateButton;
  private final GitReferenceValidator myCommitTextFieldValidator;
  private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final VcsNotifier myNotifier;

  private final Set<String> myExistingTags = new HashSet<>();
  @NonNls private static final String MESSAGE_FILE_PREFIX = "git-tag-message-";
  @NonNls private static final String MESSAGE_FILE_SUFFIX = ".txt";
  @NonNls private static final String MESSAGE_FILE_ENCODING = CharsetToolkit.UTF8;

  public GitTagDialog(Project project, List<VirtualFile> roots, VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.message("tag.title"));
    setOKButtonText(GitBundle.message("tag.button"));
    myProject = project;
    myNotifier = VcsNotifier.getInstance(myProject);
    myGit = Git.getInstance();

    GitUIUtil.setupRootChooser(myProject, roots, defaultRoot, myGitRootComboBox, myCurrentBranch);
    myGitRootComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        fetchTags();
        validateFields();
      }
    });
    fetchTags();
    myTagNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull final DocumentEvent e) {
        validateFields();
      }
    });
    myCommitTextFieldValidator = new GitReferenceValidator(project, myGitRootComboBox, myCommitTextField, myValidateButton,
                                                           () -> validateFields());
    myForceCheckBox.addActionListener(new ActionListener() {
      @Override
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

  public void runAction() {
    final String message = myMessageTextArea.getText();
    final boolean hasMessage = message.trim().length() != 0;
    final File messageFile;
    if (hasMessage) {
      try {
        messageFile = FileUtil.createTempFile(MESSAGE_FILE_PREFIX, MESSAGE_FILE_SUFFIX);
        messageFile.deleteOnExit();
        try (Writer out = new OutputStreamWriter(new FileOutputStream(messageFile), MESSAGE_FILE_ENCODING)) {
          out.write(message);
        }
      }
      catch (IOException ex) {
        myNotifier.notifyError(TAG_NOT_CREATED,
                               GitBundle.message("git.tag.could.not.create.tag"),
                               GitBundle.message("tag.error.creating.message.file.message", ex.toString())
        );
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
        h.addParameters("-F");
        h.addAbsoluteFile(messageFile);
      }
      h.addParameters(myTagNameTextField.getText());
      String object = myCommitTextField.getText().trim();
      if (object.length() != 0) {
        h.addParameters(object);
      }

      GitCommandResult result = myGit.runCommand(h);
      if (result.success()) {
        myNotifier.notifySuccess(TAG_CREATED,
                                 myTagNameTextField.getText(),
                                 GitBundle.message("git.tag.created.tag.successfully", myTagNameTextField.getText())
        );
      }
      else {
        myNotifier.notifyError(TAG_NOT_CREATED,
                               GitBundle.message("git.tag.could.not.create.tag"),
                               result.getErrorOutputAsHtmlString(),
                               true);
      }

      GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(getGitRoot());
      if (repository != null) {
        repository.getRepositoryFiles().refreshTagsFiles();
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

  private void validateFields() {
    String text = myTagNameTextField.getText();
    if (myExistingTags.contains(text)) {
      myForceCheckBox.setEnabled(true);
      if (!myForceCheckBox.isSelected()) {
        setErrorText(GitBundle.message("tag.error.tag.exists"));
        setOKActionEnabled(false);
        return;
      }
    }
    else {
      myForceCheckBox.setEnabled(false);
      myForceCheckBox.setSelected(false);
    }
    if (myCommitTextFieldValidator.isInvalid()) {
      setErrorText(GitBundle.message("tag.error.invalid.commit"));
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

  private void fetchTags() {
    myExistingTags.clear();

    try {
      myExistingTags.addAll(ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> GitBranchUtil.getAllTags(myProject, getGitRoot()),
        GitBundle.message("tag.getting.existing.tags"),
        false,
        myProject));
    }
    catch (VcsException e) {
      GitUIUtil.showOperationError(myProject, GitBundle.message("tag.getting.existing.tags"), e.getMessage());
      throw new ProcessCanceledException();
    }
  }

  private VirtualFile getGitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.TagFiles";
  }
}
