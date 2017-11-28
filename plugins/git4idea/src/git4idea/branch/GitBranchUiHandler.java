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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitCommit;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <p>Handles UI interaction during various operations on branches: shows notifications, proposes to rollback, shows dialogs, messages, etc.
 * Some methods return the choice selected by user to the calling code, if it is needed.</p>
 * <p>The purpose of this class is to separate UI interaction from the main code, which would in particular simplify testing.</p>
 */
public interface GitBranchUiHandler {

  @NotNull
  ProgressIndicator getProgressIndicator();

  boolean notifyErrorWithRollbackProposal(@NotNull String title, @NotNull String message, @NotNull String rollbackProposal);

  /**
   * Shows notification about unmerged files preventing checkout, merge, etc.
   * @param operationName
   * @param repositories
   */
  void showUnmergedFilesNotification(@NotNull String operationName, @NotNull Collection<GitRepository> repositories);

  /**
   * Shows a modal notification about unmerged files preventing an operation, with "Rollback" button.
   * Pressing "Rollback" would should the operation which has already successfully executed on other repositories.
   *
   * @return true if user has agreed to rollback, false if user denied the rollback proposal.
   * @param operationName
   * @param rollbackProposal
   */
  boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal);

  /**
   * Show notification about "untracked files would be overwritten by merge/checkout".
   */
  void showUntrackedFilesNotification(@NotNull String operationName, @NotNull VirtualFile root, @NotNull Collection<String> relativePaths);

  boolean showUntrackedFilesDialogWithRollback(@NotNull String operationName, @NotNull String rollbackProposal,
                                               @NotNull VirtualFile root, @NotNull Collection<String> relativePaths);

  /**
   * Shows the dialog proposing to execute the operation (checkout or merge) smartly, i.e. stash-execute-unstash.
   *
   * @param project
   * @param changes          local changes that would be overwritten by checkout or merge.
   * @param paths            paths reported by Git (in most cases this is covered by {@code changes}.
   * @param operation        operation name: checkout or merge
   * @param forceButtonTitle if the operation can be executed force (force checkout is possible),
   *                         specify the title of the force button; otherwise (force merge is not possible) pass null.
   * @return the code of the decision.
   */
  @NotNull
  GitSmartOperationDialog.Choice showSmartOperationDialog(@NotNull Project project,
                                                          @NotNull List<Change> changes,
                                                          @NotNull Collection<String> paths,
                                                          @NotNull String operation,
                                                          @Nullable String forceButtonTitle);

  /**
   * @return true if user decided to restore the branch.
   */
  boolean showBranchIsNotFullyMergedDialog(@NotNull Project project,
                                           @NotNull Map<GitRepository, List<GitCommit>> history,
                                           @NotNull Map<GitRepository, String> baseBranches,
                                           @NotNull String removedBranch);

  /**
   * <p>Show confirmation about deleting of a remote branch.</p>
   * <p>If there is a common tracking branch of this remote branch, the confirmation proposes to delete it as well.</p>
   */
  @NotNull
  DeleteRemoteBranchDecision confirmRemoteBranchDeletion(@NotNull String branchName,
                                                         @NotNull Collection<String> trackingBranches,
                                                         @NotNull Collection<GitRepository> repositories);

  enum DeleteRemoteBranchDecision {
    CANCEL,
    DELETE,
    DELETE_WITH_TRACKING
  }
}
