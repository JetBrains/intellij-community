/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.DialogManager;
import git4idea.GitCommit;
import git4idea.repo.GitRepository;
import git4idea.ui.GitCommitListWithDiffPanel;
import git4idea.ui.GitRepositoryComboboxListCellRenderer;
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
 *
 * @author Kirill Likhodedov
 */
public class GitBranchIsNotFullyMergedDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(GitBranchIsNotFullyMergedDialog.class);

  private final Project myProject;
  private final Map<GitRepository, List<GitCommit>> myCommits;

  private final GitCommitListWithDiffPanel myCommitListWithDiffPanel;
  private final Collection<GitRepository> myRepositories;
  @NotNull private final String myRemovedBranch;
  @NotNull private final Map<GitRepository, String> myBaseBranches;
  private final GitRepository myInitialRepository;

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
    myProject = project;
    myCommits = commits;
    myRepositories = commits.keySet();
    myBaseBranches = baseBranches;
    myRemovedBranch = removedBranch;

    myInitialRepository = calcInitiallySelectedRepository();
    myCommitListWithDiffPanel = new GitCommitListWithDiffPanel(myProject, new ArrayList<>(myCommits.get(myInitialRepository)));

    init();

    setTitle("Branch Was Not Fully Merged");
    setOKButtonText("Restore");
    setOKButtonMnemonic('R');
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
      description = String.format("All commits from branch %s were merged", myRemovedBranch);
    }
    else {
      description = String.format("The branch %s was not fully merged to %s.<br/>Below is the list of unmerged commits.",
                                  myRemovedBranch, baseBranch);
    }
    return XmlStringUtil.wrapInHtml(description);
  }

  @Override
  protected JComponent createNorthPanel() {
    JBLabel descriptionLabel = new JBLabel(makeDescription(myInitialRepository));

    JComboBox repositorySelector = new JComboBox(ArrayUtil.toObjectArray(myRepositories, GitRepository.class));
    repositorySelector.setRenderer(new GitRepositoryComboboxListCellRenderer(repositorySelector));
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
    JBLabel label = new JBLabel("Repository: ");
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

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    return new Action[] { getOKAction() };
  }

  @NotNull
  @Override
  protected Action[] createActions() {
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
