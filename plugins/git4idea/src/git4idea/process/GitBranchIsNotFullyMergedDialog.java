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
package git4idea.process;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import git4idea.history.browser.GitCommit;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.ui.GitCommitListWithDiffPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * This dialog is shown when user tries to delete a local branch, which is not fully merged to the current branch.
 * It shows the list of commits not merged to the current branch and the list of branches, which the given branch is merged to.
 *
 * @author Kirill Likhodedov
 */
public class GitBranchIsNotFullyMergedDialog extends DialogWrapper {

  private final Project myProject;
  private final List<GitCommit> myCommits;
  private final GitRepository myRepository;
  private final String myBranchToDelete;
  private final List<String> myMergedToBranches;

  private final JPanel myRootPanel;
  private final GitCommitListWithDiffPanel myCommitListWithDiffPanel;

  /**
   * Show the dialog and get user's answer, whether he wants to force delete the branch.
   * @param commits          the list of commits, which are not merged from the branch being deleted to the current branch.
   * @param currentBranch    the name of the current branch.
   * @param branchToDelete   the name of the branch which user chose to delete.
   * @param mergedToBranches the list of branches which the branch is merged to (returned by {@code git branch --merged <branchToDelete>} command.
   * @return true if user decided to delete the branch.
   */
  public static boolean showAndGetAnswer(@NotNull Project project,
                                         @NotNull List<GitCommit> commits,
                                         @NotNull GitRepository repository,
                                         @NotNull String branchToDelete,
                                         @NotNull List<String> mergedToBranches) {
    GitBranchIsNotFullyMergedDialog dialog = new GitBranchIsNotFullyMergedDialog(project, commits, repository, branchToDelete, mergedToBranches);
    dialog.show();
    return dialog.isOK();
  }

  private GitBranchIsNotFullyMergedDialog(@NotNull Project project, @NotNull List<GitCommit> commits, @NotNull GitRepository repository, @NotNull String branchToDelete, @NotNull List<String> mergedToBranches) {
    super(project, false);
    myProject = project;
    myCommits = commits;
    myRepository = repository;
    myBranchToDelete = branchToDelete;
    myMergedToBranches = mergedToBranches;

    myCommitListWithDiffPanel = new GitCommitListWithDiffPanel(myProject, myCommits);
    JBLabel descriptionLabel = new JBLabel("<html>" + makeDescription() + "</html>");

    myRootPanel = new JPanel(new BorderLayout());
    myRootPanel.add(descriptionLabel, BorderLayout.NORTH);
    myRootPanel.add(myCommitListWithDiffPanel);

    init();

    setTitle("Branch is not fully merged");
    setOKButtonText("Delete");
    setCancelButtonText("Cancel");
  }

  private String makeDescription() {
    StringBuilder description = new StringBuilder();
    if (myRepository.isOnBranch()) {
      assert myRepository.getCurrentBranch() != null;
      description.append(GitBundle.message("branch.delete.not_fully_merged.description", myBranchToDelete, myRepository.getCurrentBranch().getName()));
    } else {
      description.append(GitBundle.message("branch.delete.not_fully_merged.description.not_on_branch", myBranchToDelete, myRepository.getCurrentRevision()));
    }
    if (!myMergedToBranches.isEmpty()) {
      String listOfMergedBranches = StringUtil.join(StringUtil.surround(ArrayUtil.toStringArray(myMergedToBranches), "<b>", "</b>"), ", ");
      description.append("<br>");
      if (myMergedToBranches.size() == 1) {
        description.append(GitBundle.message("branch.delete.merged_to.one", myBranchToDelete, listOfMergedBranches));
      }
      else {
        description.append(GitBundle.message("branch.delete.merged_to.many", myBranchToDelete, listOfMergedBranches));
      }
    }
    description.append("<br>").append(GitBundle.message("branch.delete.warning", myBranchToDelete));
    return description.toString();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override
  protected Action[] createLeftSideActions() {
    return new Action[] { getOKAction() };
  }

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
