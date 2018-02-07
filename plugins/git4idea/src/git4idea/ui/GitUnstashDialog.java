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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.stash.GitStashUtils;
import git4idea.util.GitUIUtil;
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
  private static final Logger LOG = Logger.getInstance(GitUnstashDialog.class);

  public GitUnstashDialog(final Project project, final List<VirtualFile> roots, final VirtualFile defaultRoot) {
    super(project, true);
    setModal(false);
    myProject = project;
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
      public void actionPerformed(final ActionEvent e) {
        final StashInfo stash = getSelectedStash();
        if (Messages.YES == Messages.showYesNoDialog(GitUnstashDialog.this.getContentPane(),
                                                     GitBundle.message("git.unstash.drop.confirmation.message", stash.getStash(), stash.getMessage()),
                                                     GitBundle.message("git.unstash.drop.confirmation.title", stash.getStash()), Messages.getQuestionIcon())) {
          final ModalityState current = ModalityState.current();
          ProgressManager.getInstance().run(new Task.Modal(myProject, "Removing stash " + stash.getStash(), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              final GitLineHandler h = dropHandler(stash.getStash());
              try {
                Git.getInstance().runCommand(h).getOutputOrThrow();
              }
              catch (final VcsException ex) {
                ApplicationManager.getApplication().invokeLater(() -> GitUIUtil.showOperationError(myProject, ex, h.printableCommandLine()), current);
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
      public void actionPerformed(final ActionEvent e) {
        final VirtualFile root = getGitRoot();
        String resolvedStash;
        String selectedStash = getSelectedStash().getStash();
        try {
          GitLineHandler h = new GitLineHandler(project, root, GitCommand.REV_LIST);
          h.setSilent(true);
          h.addParameters("--timestamp", "--max-count=1", selectedStash);
          h.endOptions();
          final String output = Git.getInstance().runCommand(h).getOutputOrThrow();
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
    GitStashUtils.loadStashStack(myProject, root, stashInfo -> listModel.addElement(stashInfo));
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
    h.addParameters(selectedStash);
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
    GitLineHandler h = handler();

    boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      GitStashUtils.unstash(myProject, root, h, new UnstashConflictResolver(myProject, root, getSelectedStash()));
    }, GitBundle.getString("unstash.unstashing"), true, myProject);

    if (completed) {
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

    public UnstashConflictResolver(Project project, VirtualFile root, StashInfo stashInfo) {
      super(project, Git.getInstance(),
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
