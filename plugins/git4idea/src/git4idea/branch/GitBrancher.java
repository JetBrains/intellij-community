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

import org.jetbrains.annotations.NotNull;

/**
 * Executes various operations on Git branches: checkout, create new branch, merge, delete, compare.
 * All operations can be called from the EDT: the GitBrancher will take care of starting a background task.
 * It also takes care of analyzing results and notifying the user.
 *
 * @author Kirill Likhodedov
 */
public interface GitBrancher {

  /**
   * <p>Checks out a new branch in background.
   *    If there are unmerged files, proposes to resolve the conflicts and tries to check out again.</p>
   * <p>Doesn't check the name of new branch for validity -
   *    do this before calling this method, otherwise a standard error dialog will be shown.</p>
   * <p>Equivalent to {@code git checkout <name>}</p>
   *
   * @param name Name of the new branch to check out.
   */
  void checkoutNewBranch(@NotNull String name);

  /**
   *
   * @param name
   * @param reference
   */
  void createNewTag(@NotNull String name, @NotNull String reference);

  /**
   * <p>Checks out the given reference (a branch, or a reference name, or a commit hash).
   *    If local changes prevent the checkout, shows the list of them and proposes to make a "smart checkout":
   *    stash-checkout-unstash.</p>
   * <p>Doesn't check the reference for validity.</p>
   *
   * @param reference reference to be checked out.
   */
  void checkout(@NotNull String reference);

  /**
   * Creates and checks out a new local branch starting from the given reference:
   * {@code git checkout -b <branchname> <start-point>}. <br/>
   * Provides the "smart checkout" procedure the same as in {@link #checkout(String)}.
   *
   * @param newBranchName     the name of the new local branch.
   * @param startPoint        the reference to checkout.
   */
  void checkoutNewBranchStartingFrom(@NotNull String newBranchName, @NotNull String startPoint);

  /**
   * <p>Deletes the branch with the specified name.</p>
   * <p>{@code git branch -d <name>}</p>
   * <p>If the branch can't be deleted, because it is unmerged neither to the HEAD nor to its upstream,
   *    displays a dialog showing commits that are not merged and proposing to execute force deletion:</p>
   * <p>{@code git branch -D <name>}</p>
   *
   * @param branchName the name of the branch to be deleted.
   */
  void deleteBranch(String branchName);

  /**
   * <p>Deletes the remote branch:</p>
   * <p>{@code git push <remote> :<name>}</p>
   * @param branchName name of the remote branch to delete.
   */
  void deleteRemoteBranch(@NotNull String branchName);

  /**
   * Compares the HEAD with the specified branch - shows a dialog with the differences.
   *
   * @param branchName name of the branch to compare with.
   */
  void compare(@NotNull String branchName);

  /**
   * <p>Merges the given branch to the HEAD.</p>
   * <p>{@code git merge <name>}</p>
   * <p>If local changes prevent merging, proposes the "Smart merge" procedure (stash-merge-unstash).</p>
   * <p>If untracked files prevent merging, shows them in an error dialog.</p>
   *
   * @param branchName  the branch to be merged into HEAD.
   * @param localBranch true indicates that the merged branch is a local branch, false - that it is a remote branch.
   *                    After a local branch is merged, the IDE proposes to delete it at once (common feature branches workflow),
   *                    but it is not done for remote branches and for master.
   */
  void merge(@NotNull String branchName, boolean localBranch);

}
