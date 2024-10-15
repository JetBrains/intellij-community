// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.openapi.project.Project;
import git4idea.GitReference;
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
    return project.getService(GitBrancher.class);
  }

  /**
   * <p>Checks out a new branch in background.
   * If there are unmerged files, proposes to resolve the conflicts and tries to check out again.</p>
   * <p>Doesn't check the name of new branch for validity -
   * do this before calling this method, otherwise a standard error dialog will be shown.</p>
   * <p>Equivalent to {@code git checkout <name>}</p>
   *
   * @param name         name of the new branch to check out.
   * @param repositories repositories to operate on.
   */
  void checkoutNewBranch(@NotNull String name, @NotNull List<? extends @NotNull GitRepository> repositories);

  /**
   * Creates new branch without checking it out.
   *
   * @param name           name of the new branch.
   * @param startPoints    position (commit hash) where the branch should be created, for each repository.
   */
  void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints);

  /**
   * Creates new branch without checking it out.
   *
   * @param name           name of the new branch.
   * @param startPoints    position (commit hash) where the branch should be created, for each repository.
   *                       Such position can be indicated by any valid Git reference (commit hash, branch name, etc.)
   * @param callInAwtLater the Runnable that should be called after execution of the method (both successful and unsuccessful).
   */
  void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints, @Nullable Runnable callInAwtLater);

  /**
   * Creates new branch without checking it out.
   *
   * @param name           name of the new branch.
   * @param startPoints    position (commit hash) where the branch should be created, for each repository.
   *                       Such position can be indicated by any valid Git reference (commit hash, branch name, etc.)
   * @param force          create and overwrite existing if needed
   */
  void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints, boolean force);
  /**
   * Creates new branch without checking it out.
   *
   * @param name           name of the new branch.
   * @param startPoints    position (commit hash) where the branch should be created, for each repository.
   *                       Such position can be indicated by any valid Git reference (commit hash, branch name, etc.)
   * @param force          create and overwrite existing if needed
   * @param callInAwtLater the Runnable that should be called after execution of the method (both successful and unsuccessful).
   */
  void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints, boolean force, @Nullable Runnable callInAwtLater);

  /**
   * <p>Creates new tag on the selected reference.</p>
   *
   * @param name           the name of new tag.
   * @param reference      the reference which tag will point to.
   * @param repositories   repositories to operate on.
   * @param callInAwtLater the Runnable that should be called after execution of the method (both successful and unsuccessful).
   *                       If given, it will be called in the EDT {@link javax.swing.SwingUtilities#invokeLater(Runnable) later}.
   */
  void createNewTag(@NotNull String name, @NotNull String reference, @NotNull List<? extends @NotNull GitRepository> repositories,
                    @Nullable Runnable callInAwtLater);

  /**
   * <p>Checks out the given reference (a branch, or a reference name, or a commit hash).
   * If local changes prevent the checkout, shows the list of them and proposes to make a "smart checkout":
   * stash-checkout-unstash.</p>
   * <p>Doesn't check the reference for validity.</p>
   *
   * @param reference      reference to be checked out.
   * @param detach         if true, checkout operation will put the repository into the detached HEAD state
   *                       (useful if one wants to checkout a remote branch position, but not create a new tracking local branch);
   *                       if false, it will behave the same as {@code git checkout} command does, i.e. switch to the local branch,
   *                       create a local branch tracking the given remote branch, checkout hash or tag into the detached HEAD.
   * @param repositories   repositories to operate on.
   * @param callInAwtLater the Runnable that should be called after execution of the method (both successful and unsuccessful).
   *                       If given, it will be called in the EDT {@link javax.swing.SwingUtilities#invokeLater(Runnable) later}.
   */
  void checkout(@NotNull String reference, boolean detach, @NotNull List<? extends @NotNull GitRepository> repositories,
                @Nullable Runnable callInAwtLater);

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
  void checkoutNewBranchStartingFrom(@NotNull String newBranchName,
                                     @NotNull String startPoint,
                                     @NotNull List<? extends @NotNull GitRepository> repositories,
                                     @Nullable Runnable callInAwtLater);


  /**
   * Creates and checks out a new local branch starting from the given reference:
   * {@code git checkout -b <branchname> <start-point>}. <br/>
   * Provides the "smart checkout" procedure the same as in {@link #checkout(String, boolean, List, Runnable)}.
   *
   * @param newBranchName     the name of the new local branch.
   * @param startPoint        the reference to checkout.
   * @param overwriteIfNeeded reset existing branch to {@code startPoint} if needed.
   * @param repositories      repositories to operate on.
   * @param callInAwtLater    the Runnable that should be called after execution of the method (both successful and unsuccessful).
   *                          If given, it will be called in the EDT {@link javax.swing.SwingUtilities#invokeLater(Runnable) later}.
   */
  void checkoutNewBranchStartingFrom(@NotNull String newBranchName,
                                     @NotNull String startPoint, boolean overwriteIfNeeded,
                                     @NotNull List<? extends @NotNull GitRepository> repositories,
                                     @Nullable Runnable callInAwtLater);

  /**
   * <p>Deletes the branch with the specified name.</p>
   * <p>{@code git branch -d <name>}</p>
   * <p>If the branch can't be deleted, because it is unmerged neither to the HEAD nor to its upstream,
   * displays a dialog showing commits that are not merged and proposing to execute force deletion:</p>
   * <p>{@code git branch -D <name>}</p>
   *
   * @param branchName   the name of the branch to be deleted.
   * @param repositories repositories to operate on.
   */
  void deleteBranch(@NotNull String branchName, @NotNull List<? extends @NotNull GitRepository> repositories);

  void deleteBranches(@NotNull Map<String, List<? extends GitRepository>> branchesToContainingRepositories,
                      @Nullable Runnable callInAwtAfterExecution);

  /**
   * <p>Deletes the remote branch:</p>
   * <p>{@code git push <remote> :<name>}</p>
   *
   * @param branchName   name of the remote branch to delete.
   * @param repositories Repositories to operate on.
   */
  void deleteRemoteBranch(@NotNull String branchName, @NotNull List<? extends @NotNull GitRepository> repositories);

  /**
   * Delete multiple remote branches.
   *
   * @see #deleteRemoteBranch
   */
  void deleteRemoteBranches(@NotNull List<String> branchNames, @NotNull List<? extends @NotNull GitRepository> repositories);

  /**
   * Compares commits from the HEAD with the specified branch.
   */
  void compare(@NotNull String branchName, @NotNull List<? extends @NotNull GitRepository> repositories);

  /**
   * Compares 2 specified branches commit-wise.
   */
  void compareAny(@NotNull String branchName,
                  @NotNull String otherBranchName,
                  @NotNull List<? extends @NotNull GitRepository> repositories);

  /**
   * Compares the current working tree with its state in the selected branch HEAD.
   */
  void showDiffWithLocal(@NotNull String branchName, @NotNull List<? extends @NotNull GitRepository> repositories);

  /**
   * Compares working tree states in the selected branch HEADs.
   */
  void showDiff(@NotNull String branchName, @NotNull String otherBranchName, @NotNull List<? extends @NotNull GitRepository> repositories);

  /**
   * <p>Merges the given branch to the HEAD.</p>
   * <p>{@code git merge <name>}</p>
   * <p>If local changes prevent merging, proposes the "Smart merge" procedure (stash-merge-unstash).</p>
   * <p>If untracked files prevent merging, shows them in an error dialog.</p>
   *
   * @param reference   local/remote branch or tag to be merged into HEAD.
   * @param deleteOnMerge specify whether the branch should be automatically deleted or proposed to be deleted after merge.
   * @param repositories  repositories to operate on.
   */
  void merge(@NotNull GitReference reference,
             @NotNull DeleteOnMergeOption deleteOnMerge,
             @NotNull List<? extends @NotNull GitRepository> repositories);

  /**
   * <p>Merges the given branch to the HEAD.</p>
   * <p>{@code git merge <name>}</p>
   * <p>If local changes prevent merging, proposes the "Smart merge" procedure (stash-merge-unstash).</p>
   * <p>If untracked files prevent merging, shows them in an error dialog.</p>
   *
   * @param reference     local/remote branch or tag to be merged into HEAD.
   * @param deleteOnMerge specify whether the branch should be automatically deleted or proposed to be deleted after merge.
   * @param repositories  repositories to operate on.
   * @param allowRollback whether to prompt the user to rollback on conflicts. Useful to set to `false` when prompting is not necessary.
   */
  void merge(@NotNull GitReference reference,
             @NotNull DeleteOnMergeOption deleteOnMerge,
             @NotNull List<? extends @NotNull GitRepository> repositories,
             boolean allowRollback);

  /**
   * @deprecated use {@link #merge(GitReference, DeleteOnMergeOption, List)}
   */
  @Deprecated
  void merge(@NotNull String branchName,
             @NotNull DeleteOnMergeOption deleteOnMerge,
             @NotNull List<? extends @NotNull GitRepository> repositories);

  /**
   * Call {@code git rebase <branchName>} for each of the given repositories.
   */
  void rebase(@NotNull List<? extends @NotNull GitRepository> repositories, @NotNull String branchName);

  /**
   * Call {@code git rebase <upstream> <branchName>} for each of the given repositories
   */
  void rebase(@NotNull List<? extends @NotNull GitRepository> repositories, @NotNull String upstream, @NotNull String branchName);

  /**
   * Call {@code git rebase <current branch> <branchName>} for each of the given repositories.
   */
  void rebaseOnCurrent(@NotNull List<? extends @NotNull GitRepository> repositories, @NotNull String branchName);

  /**
   * Renames the given branch.
   */
  void renameBranch(@NotNull String currentName, @NotNull String newName, @NotNull List<? extends @NotNull GitRepository> repositories);

  /**
   * Deletes tag
   */
  void deleteTag(@NotNull String name, @NotNull List<? extends @NotNull GitRepository> repositories);

  void deleteTags(@NotNull Map<String, List<? extends GitRepository>> tagsToContainingRepositories);

  /**
   * Deletes tag on all remotes
   *
   * @param repositories map from repository to expected tag commit for --force-with-lease
   *                     null will delete tag without explicit check
   */
  void deleteRemoteTag(@NotNull String name, @NotNull Map<GitRepository, String> repositories);

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
