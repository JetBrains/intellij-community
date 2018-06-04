/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * <p>Executes various operations on Git branches: checkout, create new branch, merge, delete, compare.</p>
 * <p>All operations are asynchronous and can be called from the EDT: the GitBrancher will start a background task.</p>
 * <p>It also takes care of analyzing results and notifying the user.</p>
 * <p>All operations can be called for multiple repositories at once.</p>
 *
 * @see GitBranchWorker
 */
public interface GitBrancher {
  static GitBrancher getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitBrancher.class);
  }

  /**
   * <p>Checks out a new branch in background.
   *    If there are unmerged files, proposes to resolve the conflicts and tries to check out again.</p>
   * <p>Doesn't check the name of new branch for validity -
   *    do this before calling this method, otherwise a standard error dialog will be shown.</p>
   * <p>Equivalent to {@code git checkout <name>}</p>
   *
   * @param name          name of the new branch to check out.
   * @param repositories  repositories to operate on.
   */
  void checkoutNewBranch(@NotNull String name, @NotNull List<GitRepository> repositories);

  /**
   * Creates new branch without checking it out.
   *
   * @param name name of the new branch.
   * @param startPoints position (commit hash) where the branch should be created, for each repository.
   *                    Such position can be indicated by any valid Git reference (commit hash, branch name, etc.)
   */
  void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints);

  /**
   * <p>Creates new tag on the selected reference.</p>
   *
   * @param name           the name of new tag.
   * @param reference      the reference which tag will point to.
   * @param repositories   repositories to operate on.
   * @param callInAwtLater the Runnable that should be called after execution of the method (both successful and unsuccessful).
   *                       If given, it will be called in the EDT {@link javax.swing.SwingUtilities#invokeLater(Runnable) later}.
   */
  void createNewTag(@NotNull String name, @NotNull String reference, @NotNull List<GitRepository> repositories,
                    @Nullable Runnable callInAwtLater);

  /**
   * <p>Checks out the given reference (a branch, or a reference name, or a commit hash).
   *    If local changes prevent the checkout, shows the list of them and proposes to make a "smart checkout":
   *    stash-checkout-unstash.</p>
   * <p>Doesn't check the reference for validity.</p>
   * @param reference      reference to be checked out.
   * @param detach         if true, checkout operation will put the repository into the detached HEAD state
   *                       (useful if one wants to checkout a remote branch position, but not create a new tracking local branch);
   *                       if false, it will behave the same as {@code git checkout} command does, i.e. switch to the local branch,
   *                       create a local branch tracking the given remote branch, checkout hash or tag into the detached HEAD.
   * @param repositories   repositories to operate on.
   * @param callInAwtLater the Runnable that should be called after execution of the method (both successful and unsuccessful).
 *                       If given, it will be called in the EDT {@link javax.swing.SwingUtilities#invokeLater(Runnable) later}.
   */
  void checkout(@NotNull String reference, boolean detach, @NotNull List<GitRepository> repositories, @Nullable Runnable callInAwtLater);

  /**
   * Creates and checks out a new local branch starting from the given reference:
   * {@code git checkout -b <branchname> <start-point>}. <br/>
   * Provides the "smart checkout" procedure the same as in {@link #checkout(String, boolean, List, Runnable)}.
   *
   * @param newBranchName  the name of the new local branch.
   * @param startPoint     the reference to checkout.
   * @param repositories   repositories to operate on.
   * @param callInAwtLater the Runnable that should be called after execution of the method (both successful and unsuccessful).
   *                       If given, it will be called in the EDT {@link javax.swing.SwingUtilities#invokeLater(Runnable) later}.
   */
  void checkoutNewBranchStartingFrom(@NotNull String newBranchName, @NotNull String startPoint, @NotNull List<GitRepository> repositories,
                                     @Nullable Runnable callInAwtLater);

  /**
   * <p>Deletes the branch with the specified name.</p>
   * <p>{@code git branch -d <name>}</p>
   * <p>If the branch can't be deleted, because it is unmerged neither to the HEAD nor to its upstream,
   *    displays a dialog showing commits that are not merged and proposing to execute force deletion:</p>
   * <p>{@code git branch -D <name>}</p>
   *
   * @param branchName   the name of the branch to be deleted.
   * @param repositories repositories to operate on.
   */
  void deleteBranch(@NotNull String branchName, @NotNull List<GitRepository> repositories);

  /**
   * <p>Deletes the remote branch:</p>
   * <p>{@code git push <remote> :<name>}</p>
   *
   * @param branchName   name of the remote branch to delete.
   * @param repositories Repositories to operate on.
   */
  void deleteRemoteBranch(@NotNull String branchName, @NotNull List<GitRepository> repositories);

  /**
   * Compares the HEAD with the specified branch - shows a dialog with the differences.
   *
   * @param branchName         name of the branch to compare with.
   * @param repositories       repositories to operate on.
   * @param selectedRepository current or selected repository.
   *                           The list of commits is displayed for the repository selected from the combobox.
   *                           This parameter tells which repository should be pre-selected in the combobox.
   */
  void compare(@NotNull String branchName, @NotNull List<GitRepository> repositories, @NotNull GitRepository selectedRepository);

  /**
   * <p>Merges the given branch to the HEAD.</p>
   * <p>{@code git merge <name>}</p>
   * <p>If local changes prevent merging, proposes the "Smart merge" procedure (stash-merge-unstash).</p>
   * <p>If untracked files prevent merging, shows them in an error dialog.</p>
   *
   * @param branchName    the branch to be merged into HEAD.
   * @param deleteOnMerge specify whether the branch should be automatically deleted or proposed to be deleted after merge.
   * @param repositories  repositories to operate on.
   */
  void merge(@NotNull String branchName, @NotNull DeleteOnMergeOption deleteOnMerge, @NotNull List<GitRepository> repositories);

  /**
   * Call {@code git rebase <branchName>} for each of the given repositories.
   */
  void rebase(@NotNull List<GitRepository> repositories, @NotNull String branchName);

  /**
   * Call {@code git rebase <current branch> <branchName>} for each of the given repositories.
   */
  void rebaseOnCurrent(@NotNull List<GitRepository> repositories, @NotNull String branchName);

  /**
   * Renames the given branch.
   */
  void renameBranch(@NotNull String currentName, @NotNull String newName, @NotNull List<GitRepository> repositories);

  /**
   * Deletes tag
   */
  void deleteTag(@NotNull String name, @NotNull List<GitRepository> repositories);

  /**
   * Deletes tag on all remotes
   */
  void deleteRemoteTag(@NotNull String name, @NotNull List<GitRepository> repositories);

  /**
   * What should be done after successful merging a branch: delete the merged branch, propose to delete or do nothing.
   */
  enum DeleteOnMergeOption {
    /**
     * Delete the merged branch automatically.
     */
    DELETE,
    /**
     * Propose to delete the merged branch.
     */
    PROPOSE,
    /**
     * Do nothing, for example, when a remote branch has been merged, or when the {@code master} has been merged.
     */
    NOTHING
  }

}
