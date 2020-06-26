// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.dvcs.ui.RepositoryComboboxListCellRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.DialogManager;
import git4idea.GitCommit;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.ui.GitCommitListWithDiffPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This dialog is shown when user tries to delete a local branch, which is not fully merged to the current branch.
 * It shows the list of commits not merged to the current branch and the list of branches, which the given branch is merged to.
 */
public final class GitBranchIsNotFullyMergedDialog extends DialogWrapper {

  @NotNull private final Map<GitRepository, List<GitCommit>> myCommits;
  @NotNull private final GitCommitListWithDiffPanel myCommitListWithDiffPanel;
  @NotNull private final Collection<GitRepository> myRepositories;
  @NotNull private final String myRemovedBranch;
  @NotNull private final Map<GitRepository, String> myBaseBranches;
  @NotNull private final GitRepository myInitialRepository;

  /**
   * Show the dialog and get user's answer, whether he wants to force delete the branch.
   *
   * @param commits      the list of commits, which are not merged from the branch being deleted to the current branch,
   *                     grouped by repository.
   * @param baseBranches base branches (which Git reported as not containing commits from the removed branch) per repository.
   * @return true if user decided to restore the branch.
   */
  public static boolean showAndGetAnswer(@NotNull Project project,
                                         @NotNull Map<GitRepository, List<GitCommit>> commits,
                                         @NotNull Map<GitRepository, String> baseBranches,
                                         @NotNull String removedBranch) {
    GitBranchIsNotFullyMergedDialog dialog = new GitBranchIsNotFullyMergedDialog(project, commits, baseBranches, removedBranch);
    DialogManager.show(dialog);
    return dialog.isOK();
  }

  private GitBranchIsNotFullyMergedDialog(@NotNull Project project,
                                          @NotNull Map<GitRepository, List<GitCommit>> commits,
                                          @NotNull Map<GitRepository, String> baseBranches,
                                          @NotNull String removedBranch) {
    super(project, false);
    myCommits = commits;
    myRepositories = commits.keySet();
    myBaseBranches = baseBranches;
    myRemovedBranch = removedBranch;

    myInitialRepository = calcInitiallySelectedRepository();
    myCommitListWithDiffPanel = new GitCommitListWithDiffPanel(project, new ArrayList<>(myCommits.get(myInitialRepository)));

    init();

    setTitle(GitBundle.message("branch.not.fully.merged.dialog.title"));
    setOKButtonText(GitBundle.message("branch.not.fully.merged.dialog.restore.button"));
    getCancelAction().putValue(DEFAULT_ACTION, Boolean.TRUE);
  }

  @NotNull
  private GitRepository calcInitiallySelectedRepository() {
    for (GitRepository repository : myRepositories) {
      if (!myCommits.get(repository).isEmpty()) {
        return repository;
      }
    }
    throw new AssertionError("The dialog shouldn't be shown. Unmerged commits: " + myCommits);
  }

  @NotNull
  private String makeDescription(@NotNull GitRepository repository) {
    String baseBranch = myBaseBranches.get(repository);
    String description;
    if (baseBranch == null) {
      description = GitBundle.message("branch.not.fully.merged.dialog.all.commits.from.branch.were.merged", myRemovedBranch);
    }
    else {
      description = GitBundle.message("branch.not.fully.merged.dialog.the.branch.was.not.fully.merged.to", myRemovedBranch, baseBranch);
    }
    return XmlStringUtil.wrapInHtml(description);
  }

  @Override
  protected JComponent createNorthPanel() {
    JBLabel descriptionLabel = new JBLabel(makeDescription(myInitialRepository));

    JComboBox<GitRepository> repositorySelector = new JComboBox<>(myRepositories.toArray(new GitRepository[0]));
    repositorySelector.setRenderer(new RepositoryComboboxListCellRenderer());
    repositorySelector.setSelectedItem(myInitialRepository);
    repositorySelector.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        GitRepository selectedRepo = (GitRepository)repositorySelector.getSelectedItem();
        descriptionLabel.setText(makeDescription(selectedRepo));
        myCommitListWithDiffPanel.setCommits(myCommits.get(selectedRepo));
      }
    });

    JPanel repoSelectorPanel = new JPanel(new BorderLayout());
    JBLabel label = new JBLabel(GitBundle.message("branch.not.fully.merged.dialog.repository.label"));
    label.setLabelFor(repoSelectorPanel);
    repoSelectorPanel.add(label, BorderLayout.WEST);
    repoSelectorPanel.add(repositorySelector);

    if (myRepositories.size() < 2) {
      repoSelectorPanel.setVisible(false);
    }

    JPanel northPanel = new JPanel(new BorderLayout());
    northPanel.add(descriptionLabel);
    northPanel.add(repoSelectorPanel, BorderLayout.SOUTH);
    return northPanel;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.add(myCommitListWithDiffPanel);
    return rootPanel;
  }

  @Override
  protected Action @NotNull [] createLeftSideActions() {
    return new Action[] { getOKAction() };
  }

  @Override
  protected Action @NotNull [] createActions() {
    Action cancelAction = getCancelAction();
    cancelAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    return new Action[] { cancelAction };
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCommitListWithDiffPanel.getPreferredFocusComponent();
  }

  @Override
  protected String getDimensionServiceKey() {
    return GitBranchIsNotFullyMergedDialog.class.getName();
  }
}
