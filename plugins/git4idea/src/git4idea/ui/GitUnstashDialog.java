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

import com.intellij.CommonBundle;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.Consumer;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.*;
import git4idea.config.GitVersionSpecialty;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.stash.GitStashUtils;
import git4idea.util.GitUIUtil;
import git4idea.util.GitUntrackedFilesHelper;
import git4idea.util.LocalChangesWouldBeOverwrittenHelper;
import git4idea.validators.GitBranchNameValidator;
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
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.MERGE;

/**
 * The unstash dialog
 */
public class GitUnstashDialog extends DialogWrapper {
  private JComboBox myGitRootComboBox;
  private JLabel myCurrentBranch;
  private JButton myViewButton;
  private JButton myDropButton;
  private JButton myClearButton;
  private JCheckBox myPopStashCheckBox;
  private JTextField myBranchTextField;
  private JPanel myPanel;
  private JList myStashList;
  private JCheckBox myReinstateIndexCheckBox;
  /**
   * Set of branches for the current root
   */
  private final HashSet<String> myBranches = new HashSet<>();

  private final Project myProject;
  private GitVcs myVcs;
  private static final Logger LOG = Logger.getInstance(GitUnstashDialog.class);

  public GitUnstashDialog(final Project project, final List<VirtualFile> roots, final VirtualFile defaultRoot) {
    super(project, true);
    setModal(false);
    myProject = project;
    myVcs = GitVcs.getInstance(project);
    setTitle(GitBundle.getString("unstash.title"));
    setOKButtonText(GitBundle.getString("unstash.button.apply"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRootComboBox, myCurrentBranch);
    myStashList.setModel(new DefaultListModel());
    refreshStashList();
    myGitRootComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        refreshStashList();
        updateDialogState();
      }
    });
    myStashList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateDialogState();
      }
    });
    myBranchTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateDialogState();
      }
    });
    myPopStashCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateDialogState();
      }
    });
    myClearButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (Messages.YES == Messages.showYesNoDialog(GitUnstashDialog.this.getContentPane(),
                                                     GitBundle.message("git.unstash.clear.confirmation.message"),
                                                     GitBundle.message("git.unstash.clear.confirmation.title"), Messages.getWarningIcon())) {
          GitLineHandler h = new GitLineHandler(myProject, getGitRoot(), GitCommand.STASH);
          h.addParameters("clear");
          GitHandlerUtil.doSynchronously(h, GitBundle.getString("unstash.clearing.stashes"), h.printableCommandLine());
          refreshStashList();
          updateDialogState();
        }
      }
    });
    myDropButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final StashInfo stash = getSelectedStash();
        if (Messages.YES == Messages.showYesNoDialog(GitUnstashDialog.this.getContentPane(),
                                                     GitBundle.message("git.unstash.drop.confirmation.message", stash.getStash(), stash.getMessage()),
                                                     GitBundle.message("git.unstash.drop.confirmation.title", stash.getStash()), Messages.getQuestionIcon())) {
          final ModalityState current = ModalityState.current();
          ProgressManager.getInstance().run(new Task.Modal(myProject, "Removing stash " + stash.getStash(), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              final GitSimpleHandler h = dropHandler(stash.getStash());
              try {
                h.run();
                h.unsilence();
              }
              catch (final VcsException ex) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    GitUIUtil.showOperationError(myProject, ex, h.printableCommandLine());
                  }
                }, current);
              }
            }
          });
          refreshStashList();
          updateDialogState();
        }
      }

      private GitSimpleHandler dropHandler(String stash) {
        GitSimpleHandler h = new GitSimpleHandler(myProject, getGitRoot(), GitCommand.STASH);
        h.addParameters("drop");
        addStashParameter(h, stash);
        return h;
      }
    });
    myViewButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final VirtualFile root = getGitRoot();
        String resolvedStash;
        String selectedStash = getSelectedStash().getStash();
        try {
          GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.REV_LIST);
          h.setSilent(true);
          h.addParameters("--timestamp", "--max-count=1");
          addStashParameter(h, selectedStash);
          h.endOptions();
          final String output = h.run();
          resolvedStash = GitRevisionNumber.parseRevlistOutputAsRevisionNumber(h, output).asString();
        }
        catch (VcsException ex) {
          GitUIUtil.showOperationError(myProject, ex, "resolving revision");
          return;
        }
        GitUtil.showSubmittedFiles(myProject, resolvedStash, root, true, false);
      }
    });
    init();
    updateDialogState();
  }

  /**
   * Adds {@code stash@{x}} parameter to the handler, quotes it if needed.
   */
  private void addStashParameter(@NotNull GitHandler handler, @NotNull String stash) {
    if (GitVersionSpecialty.NEEDS_QUOTES_IN_STASH_NAME.existsIn(myVcs.getVersion())) {
      handler.addParameters(GeneralCommandLine.inescapableQuote(stash));
    }
    else {
      handler.addParameters(stash);
    }
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
      if (!GitBranchNameValidator.INSTANCE.checkInput(branch)) {
        setErrorText(GitBundle.getString("unstash.error.invalid.branch.name"));
        setOKActionEnabled(false);
        return;
      }
      if (myBranches.contains(branch)) {
        setErrorText(GitBundle.getString("unstash.error.branch.exists"));
        setOKActionEnabled(false);
        return;
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
    final DefaultListModel listModel = (DefaultListModel)myStashList.getModel();
    listModel.clear();
    VirtualFile root = getGitRoot();
    GitStashUtils.loadStashStack(myProject, root, new Consumer<StashInfo>() {
      @Override
      public void consume(StashInfo stashInfo) {
        listModel.addElement(stashInfo);
      }
    });
    myBranches.clear();
    GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(root);
    if (repository != null) {
      myBranches.addAll(GitBranchUtil.convertBranchesToNames(repository.getBranches().getLocalBranches()));
    }
    else {
      LOG.error("Repository is null for root " + root);
    }
    myStashList.setSelectedIndex(0);
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
    addStashParameter(h, selectedStash);
    return h;
  }

  private StashInfo getSelectedStash() {
    return (StashInfo)myStashList.getSelectedValue();
  }

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
    final GitLineHandler h = handler();
    final AtomicBoolean conflict = new AtomicBoolean();

    h.addLineListener(new GitLineHandlerAdapter() {
      public void onLineAvailable(String line, Key outputType) {
        if (line.contains("Merge conflict")) {
          conflict.set(true);
        }
      }
    });
    GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector = new GitUntrackedFilesOverwrittenByOperationDetector(root);
    GitLocalChangesWouldBeOverwrittenDetector localChangesDetector = new GitLocalChangesWouldBeOverwrittenDetector(root, MERGE);
    h.addLineListener(untrackedFilesDetector);
    h.addLineListener(localChangesDetector);

    AccessToken token = DvcsUtil.workingTreeChangeStarted(myProject);
    try {
      final Ref<GitCommandResult> result = Ref.create();
      final ProgressManager progressManager = ProgressManager.getInstance();
      boolean completed = progressManager.runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          h.addLineListener(new GitHandlerUtil.GitLineHandlerListenerProgress(progressManager.getProgressIndicator(), h, "stash", false));
          Git git = ServiceManager.getService(Git.class);
          result.set(git.runCommand(new Computable.PredefinedValueComputable<>(h)));
        }
      }, GitBundle.getString("unstash.unstashing"), true, myProject);

      if (!completed) return;

      VfsUtil.markDirtyAndRefresh(false, true, false, root);
      GitCommandResult res = result.get();
      if (conflict.get()) {
        boolean conflictsResolved = new UnstashConflictResolver(myProject, root, getSelectedStash()).merge();
        LOG.info("loadRoot " + root + ", conflictsResolved: " + conflictsResolved);
      } else if (untrackedFilesDetector.wasMessageDetected()) {
        GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(myProject, root, untrackedFilesDetector.getRelativeFilePaths(),
                                                                  "unstash", null);
      } else if (localChangesDetector.wasMessageDetected()) {
        LocalChangesWouldBeOverwrittenHelper.showErrorDialog(myProject, root, "unstash", localChangesDetector.getRelativeFilePaths());
      } else if (!res.success()) {
        GitUIUtil.showOperationErrors(myProject, h.errors(), h.printableCommandLine());
      }
    }
    finally {
      DvcsUtil.workingTreeChangeFinished(myProject, token);
    }
    super.doOKAction();
  }

  public static void showUnstashDialog(Project project, List<VirtualFile> gitRoots, VirtualFile defaultRoot) {
    new GitUnstashDialog(project, gitRoots, defaultRoot).show();
    // d is not modal=> everything else in doOKAction.
  }

  private static class UnstashConflictResolver extends GitConflictResolver {

    private final VirtualFile myRoot;
    private final StashInfo myStashInfo;

    public UnstashConflictResolver(Project project, VirtualFile root, StashInfo stashInfo) {
      super(project, ServiceManager.getService(Git.class),
            Collections.singleton(root), makeParams(stashInfo));
      myRoot = root;
      myStashInfo = stashInfo;
    }
    
    private static Params makeParams(StashInfo stashInfo) {
      Params params = new Params();
      params.setErrorNotificationTitle("Unstashed with conflicts");
      params.setMergeDialogCustomizer(new UnstashMergeDialogCustomizer(stashInfo));
      return params;
    }

    @Override
    protected void notifyUnresolvedRemain() {
      VcsNotifier.getInstance(myProject).notifyImportantWarning("Conflicts were not resolved during unstash",
                                                                "Unstash is not complete, you have unresolved merges in your working tree<br/>" +
                                                                "<a href='resolve'>Resolve</a> conflicts.", new NotificationListener() {
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

    public UnstashMergeDialogCustomizer(StashInfo stashInfo) {
      myStashInfo = stashInfo;
    }

    @Override
    public String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
      return "<html>Conflicts during unstashing <code>" + myStashInfo.getStash() + "\"" + myStashInfo.getMessage() + "\"</code></html>";
    }

    @Override
    public String getLeftPanelTitle(@NotNull VirtualFile file) {
      return "Local changes";
    }

    @Override
    public String getRightPanelTitle(@NotNull VirtualFile file, VcsRevisionNumber revisionNumber) {
      return "Changes from stash";
    }
  }
}
