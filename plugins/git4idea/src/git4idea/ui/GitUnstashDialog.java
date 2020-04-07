// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui;

import com.intellij.CommonBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.vcs.log.Hash;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.stash.GitStashUtils;
import git4idea.util.GitUIUtil;
import git4idea.validators.GitBranchValidatorKt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtmlTag;
import static java.util.Collections.singletonList;

/**
 * The unstash dialog
 */
public class GitUnstashDialog extends DialogWrapper {
  private JComboBox<VirtualFile> myGitRootComboBox;
  private JLabel myCurrentBranch;
  private JButton myViewButton;
  private JButton myDropButton;
  private JButton myClearButton;
  private JCheckBox myPopStashCheckBox;
  private JTextField myBranchTextField;
  private JPanel myPanel;
  private JList<StashInfo> myStashList;
  private final DefaultListModel<StashInfo> myStashListModel;
  private JCheckBox myReinstateIndexCheckBox;

  private final Project myProject;
  private static final Logger LOG = Logger.getInstance(GitUnstashDialog.class);

  public GitUnstashDialog(final Project project, final List<VirtualFile> roots, final VirtualFile defaultRoot) {
    super(project, true);
    setModal(false);
    myProject = project;
    setTitle(GitBundle.getString("unstash.title"));
    setOKButtonText(GitBundle.getString("unstash.button.apply"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRootComboBox, myCurrentBranch);
    myStashListModel = new DefaultListModel<>();
    myStashList.setModel(myStashListModel);
    refreshStashList();
    myGitRootComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        refreshStashList();
        updateDialogState();
      }
    });
    myStashList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        updateDialogState();
      }
    });
    myBranchTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull final DocumentEvent e) {
        updateDialogState();
      }
    });
    myPopStashCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDialogState();
      }
    });
    myClearButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        if (Messages.YES == Messages.showYesNoDialog(GitUnstashDialog.this.getContentPane(),
                                                     GitBundle.message("git.unstash.clear.confirmation.message"),
                                                     GitBundle.message("git.unstash.clear.confirmation.title"), Messages.getWarningIcon())) {
          GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.STASH);
          h.addParameters("clear");
          new Task.Modal(project, GitBundle.getString("unstash.clearing.stashes"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              GitCommandResult result = Git.getInstance().runCommand(h);
              if (!result.success()) ApplicationManager.getApplication()
                .invokeLater(() -> GitUIUtil.showOperationError(project,
                                                                  GitBundle.getString("unstash.clearing.stashes"),
                                                                  result.getErrorOutputAsJoinedString()));
            }

            @Override
            public void onFinished() {
              refreshStashList();
              updateDialogState();
            }
          }.queue();
        }
      }
    });
    myDropButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final StashInfo stash = getSelectedStash();
        if (Messages.YES == Messages.showYesNoDialog(GitUnstashDialog.this.getContentPane(),
                                                     GitBundle.message("git.unstash.drop.confirmation.message", stash.getStash(), stash.getMessage()),
                                                     GitBundle.message("git.unstash.drop.confirmation.title", stash.getStash()), Messages.getQuestionIcon())) {
          final ModalityState current = ModalityState.current();
          ProgressManager.getInstance().run(new Task.Modal(
            myProject,
            GitBundle.message("unstash.dialog.remove.stash.progress.indicator.title", stash.getStash()),
            true
          ) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              final GitLineHandler h = dropHandler(stash.getStash());
              try {
                Git.getInstance().runCommand(h).throwOnError();
              }
              catch (final VcsException ex) {
                ApplicationManager.getApplication()
                  .invokeLater(() -> GitUIUtil.showOperationError(myProject, ex, h.printableCommandLine()), current);
              }
            }
          });
          refreshStashList();
          updateDialogState();
        }
      }

      private GitLineHandler dropHandler(String stash) {
        GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.STASH);
        h.addParameters("drop", stash);
        return h;
      }
    });
    myViewButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        VirtualFile root = getGitRoot();
        String selectedStash = getSelectedStash().getStash();
        try {
          String hash = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> resolveHashOfStash(root, selectedStash),
            GitBundle.getString("unstash.dialog.stash.details.load.progress.indicator.title"),
            true,
            project
          );
          GitUtil.showSubmittedFiles(myProject, hash, root, true, false);
        }
        catch (VcsException ex) {
          GitUIUtil.showOperationError(myProject, ex, "resolving revision");
        }
      }
    });
    init();
    updateDialogState();
  }

  @NotNull
  private String resolveHashOfStash(@NotNull VirtualFile root, @NotNull String stash) throws VcsException {
    GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.REV_LIST);
    h.setSilent(true);
    h.addParameters("--timestamp", "--max-count=1", stash);
    h.endOptions();
    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    return  GitRevisionNumber.parseRevlistOutputAsRevisionNumber(h, output).asString();
  }

  /**
   * Update state dialog depending on the current state of the fields
   */
  private void updateDialogState() {
    String branch = myBranchTextField.getText();
    if (branch.length() != 0) {
      setOKButtonText(GitBundle.getString("unstash.button.branch"));
      myPopStashCheckBox.setEnabled(false);
      myPopStashCheckBox.setSelected(true);
      myReinstateIndexCheckBox.setEnabled(false);
      myReinstateIndexCheckBox.setSelected(true);
      GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRootQuick(getGitRoot());
      if (repository != null) {
        ValidationInfo branchValidationInfo = GitBranchValidatorKt.validateName(singletonList(repository), branch);
        if (branchValidationInfo != null) {
          setErrorText(branchValidationInfo.message, myBranchTextField);
          setOKActionEnabled(false);
          return;
        }
      }
    }
    else {
      if (!myPopStashCheckBox.isEnabled()) {
        myPopStashCheckBox.setSelected(false);
      }
      myPopStashCheckBox.setEnabled(true);
      setOKButtonText(
        myPopStashCheckBox.isSelected() ? GitBundle.getString("unstash.button.pop") : GitBundle.getString("unstash.button.apply"));
      if (!myReinstateIndexCheckBox.isEnabled()) {
        myReinstateIndexCheckBox.setSelected(false);
      }
      myReinstateIndexCheckBox.setEnabled(true);
    }
    if (myStashList.getModel().getSize() == 0) {
      myViewButton.setEnabled(false);
      myDropButton.setEnabled(false);
      myClearButton.setEnabled(false);
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    else {
      myClearButton.setEnabled(true);
    }
    if (myStashList.getSelectedIndex() == -1) {
      myViewButton.setEnabled(false);
      myDropButton.setEnabled(false);
      setErrorText(null);
      setOKActionEnabled(false);
      return;
    }
    else {
      myViewButton.setEnabled(true);
      myDropButton.setEnabled(true);
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  private void refreshStashList() {
    myStashListModel.clear();
    VirtualFile root = getGitRoot();
    try {
      List<StashInfo> listOfStashes = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> GitStashUtils.loadStashStack(myProject, root),
        GitBundle.getString("unstash.dialog.stash.list.load.progress.indicator.title"),
        true,
        myProject
      );

      for (StashInfo info: listOfStashes) {
        myStashListModel.addElement(info);
      }
      myStashList.setSelectedIndex(0);
    }
    catch (VcsException e) {
      LOG.warn(e);
      Messages.showErrorDialog(myProject, e.getMessage(), GitBundle.getString("unstash.dialog.show.stashes.error.dialog.title"));
    }
  }

  private VirtualFile getGitRoot() {
    return (VirtualFile)myGitRootComboBox.getSelectedItem();
  }

  private GitLineHandler handler() {
    GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.STASH);
    String branch = myBranchTextField.getText();
    if (branch.length() == 0) {
      h.addParameters(myPopStashCheckBox.isSelected() ? "pop" : "apply");
      if (myReinstateIndexCheckBox.isSelected()) {
        h.addParameters("--index");
      }
    }
    else {
      h.addParameters("branch", branch);
    }
    String selectedStash = getSelectedStash().getStash();
    h.addParameters(selectedStash);
    return h;
  }

  private StashInfo getSelectedStash() {
    return myStashList.getSelectedValue();
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
    return "reference.VersionControl.Git.Unstash";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myStashList;
  }

  @Override
  protected void doOKAction() {
    VirtualFile root = getGitRoot();
    GitLineHandler h = handler();

    boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      StashInfo stash = getSelectedStash();
      //better to use quick to keep consistent state with ui
      GitRepository repository = Objects.requireNonNull(GitRepositoryManager.getInstance(myProject).getRepositoryForRootQuick(root));
      Hash hash = Git.getInstance().resolveReference(repository, stash.getStash());
      GitStashUtils.unstash(myProject, Collections.singletonMap(root, hash), r -> h,
                            new UnstashConflictResolver(myProject, root, stash));
    }, GitBundle.getString("unstash.unstashing"), true, myProject);

    if (completed) {
      VcsNotifier.getInstance(myProject)
        .notifySuccess(VcsBundle.message("patch.apply.success.applied.text"));
      super.doOKAction();
    }
  }

  public static void showUnstashDialog(Project project, List<VirtualFile> gitRoots, VirtualFile defaultRoot) {
    new GitUnstashDialog(project, gitRoots, defaultRoot).show();
    // d is not modal=> everything else in doOKAction.
  }

  private static class UnstashConflictResolver extends GitConflictResolver {

    private final VirtualFile myRoot;
    private final StashInfo myStashInfo;

    UnstashConflictResolver(Project project, VirtualFile root, StashInfo stashInfo) {
      super(project, Collections.singleton(root), makeParams(project, stashInfo));
      myRoot = root;
      myStashInfo = stashInfo;
    }

    private static Params makeParams(Project project, StashInfo stashInfo) {
      Params params = new Params(project);
      params.setErrorNotificationTitle(GitBundle.getString("unstash.unstashed.with.conflicts.error.title"));
      params.setMergeDialogCustomizer(new UnstashMergeDialogCustomizer(stashInfo));
      return params;
    }

    @Override
    protected void notifyUnresolvedRemain() {
      VcsNotifier.getInstance(myProject).notifyImportantWarning(
        GitBundle.getString("unstash.dialog.unresolved.conflict.warning.notification.title"),
        GitBundle.getString("unstash.dialog.unresolved.conflict.warning.notification.message"),
        new NotificationListener() {
          @Override
          public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              if (event.getDescription().equals("resolve")) {
                new UnstashConflictResolver(myProject, myRoot, myStashInfo).mergeNoProceed();
              }
            }
          }
        }
      );
    }
  }

  private static class UnstashMergeDialogCustomizer extends MergeDialogCustomizer {

    private final StashInfo myStashInfo;

    UnstashMergeDialogCustomizer(StashInfo stashInfo) {
      myStashInfo = stashInfo;
    }

    @NotNull
    @Override
    public String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
      return wrapInHtml(
        GitBundle.message(
          "unstash.conflict.dialog.description.label.text",
          wrapInHtmlTag(myStashInfo.getStash() + "\"" + myStashInfo.getMessage() + "\"", "code")
        )
      );
    }

    @NotNull
    @Override
    public String getLeftPanelTitle(@NotNull VirtualFile file) {
      return GitBundle.getString("unstash.conflict.diff.dialog.left.title");
    }

    @NotNull
    @Override
    public String getRightPanelTitle(@NotNull VirtualFile file, VcsRevisionNumber revisionNumber) {
      return GitBundle.getString("unstash.conflict.diff.dialog.right.title");
    }
  }
}
