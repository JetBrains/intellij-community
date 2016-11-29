// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider.update;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.zmlx.hg4idea.HgErrorHandler.ensureSuccess;
import static org.zmlx.hg4idea.provider.update.HgUpdateType.MERGE;
import static org.zmlx.hg4idea.provider.update.HgUpdateType.ONLY_UPDATE;

public class HgRegularUpdater implements HgUpdater {

  @NotNull private final Project project;
  @NotNull private final VirtualFile repoRoot;
  @NotNull private final HgUpdateConfigurationSettings updateConfiguration;
  private static final Logger LOG = Logger.getInstance(HgRegularUpdater.class);

  public HgRegularUpdater(@NotNull Project project, @NotNull VirtualFile repository, @NotNull HgUpdateConfigurationSettings configuration) {
    this.project = project;
    this.repoRoot = repository;
    this.updateConfiguration = configuration;
  }

  public boolean update(final UpdatedFiles updatedFiles, ProgressIndicator indicator, List<VcsException> warnings)
    throws VcsException {
    indicator.setText(HgVcsMessages.message("hg4idea.progress.updating", repoRoot.getPath()));

    String defaultPath = HgUtil.getRepositoryDefaultPath(project, repoRoot);

    if (StringUtil.isEmptyOrSpaces(defaultPath)) {
      throw new VcsException(HgVcsMessages.message("hg4idea.warning.no-default-update-path", repoRoot.getPath()));
    }


    List<HgRevisionNumber> branchHeadsBeforePull = new HgHeadsCommand(project, repoRoot).executeInCurrentThread();

    if (branchHeadsBeforePull.size() > 1) {
      reportWarning(warnings, HgVcsMessages.message("hg4idea.update.warning.multipleHeadsBeforeUpdate", repoRoot.getPath()));
    }

    //TODO perhaps report a warning in this case ?
//    //if the parent of the working dir is not the tip of the current branch, the user has
//    //manually brought his working copy to some specific revision. In that case we won't touch
//    //his setup
//    if (!parentRevision.equals(currentBranchHead)) {
//      throw new VcsException("working dir not at branch tip (use \"Update to...\" to check out branch tip)");
//    }

    if (updateConfiguration.shouldPull()) {
      HgCommandExitCode pullResult = pull(repoRoot, indicator);
      if (pullResult == HgCommandExitCode.ERROR) {
        return false;
      }
    }

    List<HgRevisionNumber> parentsBeforeUpdate = new HgWorkingCopyRevisionsCommand(project).parents(repoRoot);
    if (parentsBeforeUpdate.size() > 1) {
      throw new VcsException(HgVcsMessages.message("hg4idea.update.error.uncommittedMerge", repoRoot.getPath()));
    }

    indicator.setText2(HgVcsMessages.message("hg4idea.progress.countingHeads"));

    List<HgRevisionNumber> branchHeadsAfterPull = new HgHeadsCommand(project, repoRoot).executeInCurrentThread();
    List<HgRevisionNumber> pulledBranchHeads = determinePulledBranchHeads(branchHeadsBeforePull, branchHeadsAfterPull);
    List<HgRevisionNumber> remainingOriginalBranchHeads =
      determingRemainingOriginalBranchHeads(branchHeadsBeforePull, branchHeadsAfterPull);
    HgUpdateType updateType = updateConfiguration.getUpdateType();

    if (branchHeadsAfterPull.size() > 1 && updateType != ONLY_UPDATE) {
      // merge strategy
      if (updateType == MERGE) {
        abortOnLocalChanges();
        abortOnMultiplePulledHeads(pulledBranchHeads);
        abortOnMultipleLocalHeads(remainingOriginalBranchHeads);

        HgCommandResult mergeResult = doMerge(indicator);

        if (updateConfiguration.shouldCommitAfterMerge()) {
          commitOrWarnAboutConflicts(warnings, mergeResult);
        }
      }
      //rebase strategy
      else {
        processRebase(indicator, updatedFiles);  //resolve conflicts processed during rebase
        return true;
      }
    }
    //if pull complete successfully and there are only one head, we need just update working directory to the head
    else {
      //in case of multiple heads the update will report the appropriate error
      update(repoRoot, indicator, updatedFiles, warnings);
    }
    //any kind of update could have resulted in merges and merge conflicts, so run the resolver
    resolvePossibleConflicts(updatedFiles);

    return true;
  }

  private static List<HgRevisionNumber> determingRemainingOriginalBranchHeads(List<HgRevisionNumber> branchHeadsBeforePull,
                                                                              List<HgRevisionNumber> branchHeadsAfterPull) {
    List<HgRevisionNumber> originalBranchHeadsRemaining = new ArrayList<>();
    for (HgRevisionNumber headAfterPull : branchHeadsAfterPull) {
      if (branchHeadsBeforePull.contains(headAfterPull)) {
        originalBranchHeadsRemaining.add(headAfterPull);
      }
    }
    return originalBranchHeadsRemaining;
  }

  private static List<HgRevisionNumber> determinePulledBranchHeads(List<HgRevisionNumber> branchHeadsBeforePull,
                                                                   List<HgRevisionNumber> branchHeadsAfterPull) {
    List<HgRevisionNumber> pulledBranchHeads = new ArrayList<>(branchHeadsAfterPull);
    pulledBranchHeads.removeAll(branchHeadsBeforePull);
    return pulledBranchHeads;
  }

  private void abortOnMultipleLocalHeads(List<HgRevisionNumber> originalBranchHeadsRemaining) throws VcsException {
    if (originalBranchHeadsRemaining.size() != 1) {
      throw new VcsException(HgVcsMessages.message("hg4idea.update.error.merge.multipleLocalHeads", repoRoot.getPath()));
    }
  }

  private void abortOnMultiplePulledHeads(List<HgRevisionNumber> newBranchHeadsAfterPull) throws VcsException {
    if (newBranchHeadsAfterPull.size() != 1) {
      throw new VcsException(HgVcsMessages.message("hg4idea.update.error.merge.multipleRemoteHeads", newBranchHeadsAfterPull.size(),
                                                   repoRoot.getPath()));
    }
  }

  private void updateToPulledHead(VirtualFile repo, UpdatedFiles updatedFiles, HgRevisionNumber newHead, ProgressIndicator indicator) {
    indicator.setText2(HgVcsMessages.message("hg4idea.update.progress.updating.to.pulled.head"));
    HgRevisionNumber parentBeforeUpdate = new HgWorkingCopyRevisionsCommand(project).firstParent(repo);
    HgUpdateCommand updateCommand = new HgUpdateCommand(project, repoRoot);
    updateCommand.setRevision(newHead.getChangeset());
    updateCommand.setClean(true);
    updateCommand.execute();

    HgRevisionNumber commonParent = findCommonParent(newHead, parentBeforeUpdate);
    addUpdatedFiles(repo, updatedFiles, commonParent, newHead);
  }

  private @Nullable HgRevisionNumber findCommonParent(HgRevisionNumber newHead, HgRevisionNumber parentBeforeUpdate) {
    // hg log -r 0:source --prune dest --limit 1
    final List<HgRevisionNumber> pulledRevisions = new HgMergePreviewCommand(project, newHead, parentBeforeUpdate, 1).executeInCurrentThread(repoRoot);
    if (pulledRevisions == null || pulledRevisions.isEmpty()) {
      return null;
    }
    HgRevisionNumber pulledRevision = pulledRevisions.get(0);
    final List<HgRevisionNumber> parentRevisions = new HgWorkingCopyRevisionsCommand(project).getRevisions(repoRoot, "parent", null, pulledRevision, true);
    if (parentRevisions.isEmpty()) {
      return null;
    }
    return parentRevisions.get(0);
  }

  private void commitOrWarnAboutConflicts(List<VcsException> exceptions, HgCommandResult mergeResult) throws VcsException {
    if (mergeResult.getExitValue() == 0) { //operation successful and no conflicts
      try {
        HgRepository hgRepository = HgUtil.getRepositoryForFile(project, repoRoot);
        if (hgRepository == null) {
          LOG.warn("Couldn't find repository info for " + repoRoot.getName());
          return;
        }
        new HgCommitCommand(project, hgRepository, "Automated merge").executeInCurrentThread();
      }
      catch (HgCommandException e) {
        throw new VcsException(e);
      }
    }
    else {
      reportWarning(exceptions, HgVcsMessages.message("hg4idea.update.warning.merge.conflicts", repoRoot.getPath()));
    }
  }

  private HgCommandResult doMerge(ProgressIndicator indicator) throws VcsException {
    indicator.setText2(HgVcsMessages.message("hg4idea.update.progress.merging"));
    HgRepository repository = HgUtil.getRepositoryManager(project).getRepositoryForRoot(repoRoot);
    if (repository == null) {
      LOG.error("Couldn't find repository for " + repoRoot.getName());
      return null;
    }
    HgMergeCommand mergeCommand = new HgMergeCommand(project, repository);
    //do not explicitly set the revision, that way mercurial itself checks that there are exactly
    //two heads in this branch
    //    mergeCommand.setRevision(headToMerge.getRevision());
    return mergeCommand.mergeSynchronously();
  }

  private void processRebase(ProgressIndicator indicator, final UpdatedFiles updatedFiles) throws VcsException {
    indicator.setText2(HgVcsMessages.message("hg4idea.progress.rebase"));
    HgRepository repository = HgUtil.getRepositoryManager(project).getRepositoryForRoot(repoRoot);
    if (repository == null) {
      throw new VcsException("Repository not found for root " + repoRoot);
    }
    HgRebaseCommand rebaseCommand = new HgRebaseCommand(project, repository);
    HgCommandResult result = new HgRebaseCommand(project, repository).startRebase();
    if (HgErrorUtil.isCommandExecutionFailed(result)) {
      new HgCommandResultNotifier(project).notifyError(result, "Hg Error", "Couldn't rebase repository.");
      return;
    }
    //noinspection ConstantConditions
    while (result.getExitValue() == 1) {    //if result == null isAbort will be true;
      resolvePossibleConflicts(updatedFiles);
      if (HgConflictResolver.hasConflicts(project, repoRoot) || HgErrorUtil.isNothingToRebase(result)) {
        break;
      }
      result = rebaseCommand.continueRebase();
      if (HgErrorUtil.isAbort(result)) {
        new HgCommandResultNotifier(project).notifyError(result, "Hg Error", "Couldn't continue rebasing");
        break;
      }
    }
    repository.update();
    repoRoot.refresh(true, true);
  }

  private void abortOnLocalChanges() throws VcsException {
    if (getLocalChanges().size() != 0) {
      throw new VcsException(HgVcsMessages.message("hg4idea.update.error.localchanges", repoRoot.getPath()));
    }
  }

  private void resolvePossibleConflicts(final UpdatedFiles updatedFiles) {
    new HgConflictResolver(project, updatedFiles).resolve(repoRoot);
  }

  private Set<HgChange> getLocalChanges() {
    HgStatusCommand statusCommand = new HgStatusCommand.Builder(true).unknown(false).ignored(false).build(project);
    return statusCommand.executeInCurrentThread(repoRoot);
  }

  private HgCommandExitCode pull(@NotNull VirtualFile repo, @NotNull ProgressIndicator indicator) {
    indicator.setText2(HgVcsMessages.message("hg4idea.progress.pull.with.update"));
    HgPullCommand hgPullCommand = new HgPullCommand(project, repo);
    final String defaultPath = HgUtil.getRepositoryDefaultPath(project, repo);
    hgPullCommand.setSource(defaultPath);
    return hgPullCommand.executeInCurrentThread();
  }

  private void update(@NotNull VirtualFile repo, ProgressIndicator indicator, UpdatedFiles updatedFiles, List<VcsException> warnings) throws VcsException {
    indicator.setText2(HgVcsMessages.message("hg4idea.progress.updatingworkingdir"));

    HgRevisionNumber parentBeforeUpdate = new HgWorkingCopyRevisionsCommand(project).firstParent(repo);
    HgUpdateCommand hgUpdateCommand = new HgUpdateCommand(project, repo);
    HgCommandResult updateResult = hgUpdateCommand.execute();
    String warningMessages = ensureSuccess(updateResult).getRawError();
    handlePossibleWarning(warnings, warningMessages);

    HgRevisionNumber parentAfterUpdate = new HgWorkingCopyRevisionsCommand(project).firstParent(repo);

    addUpdatedFiles(repo, updatedFiles, parentBeforeUpdate, parentAfterUpdate);
  }

  private static void handlePossibleWarning(List<VcsException> exceptions, String possibleWarning) {
    if (!StringUtil.isEmptyOrSpaces(possibleWarning)) {
      reportWarning(exceptions, possibleWarning);
    }
  }

  private static void reportWarning(List<VcsException> exceptions, String warningMessage) {
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    VcsException warningException = new VcsException(warningMessage);
    warningException.setIsWarning(true);
    exceptions.add(warningException);
  }

  private void addUpdatedFiles(VirtualFile repo, UpdatedFiles updatedFiles, HgRevisionNumber parentBeforeUpdate, HgRevisionNumber parentAfterUpdate) {
    if (parentAfterUpdate == null || parentBeforeUpdate == null) {
      return;
    }
    if (parentAfterUpdate.equals(parentBeforeUpdate)) { // nothing to update => returning not to capture local uncommitted changes
      return;
    }
    HgStatusCommand statusCommand = new HgStatusCommand.Builder(true).ignored(false).unknown(false).baseRevision(parentBeforeUpdate).targetRevision(
      parentAfterUpdate).build(project);
    Set<HgChange> changes = statusCommand.executeInCurrentThread(repo);
    for (HgChange change : changes) {
      HgFileStatusEnum status = change.getStatus();
      switch (status) {
        case ADDED:
          addToGroup(updatedFiles, change, FileGroup.CREATED_ID);
          break;
        case MODIFIED:
          addToGroup(updatedFiles, change, FileGroup.UPDATED_ID);
          break;
        case DELETED:
          addToGroup(updatedFiles, change, FileGroup.REMOVED_FROM_REPOSITORY_ID);
          break;
        case COPY:
          addToGroup(updatedFiles, change, FileGroup.CHANGED_ON_SERVER_ID);
          break;
        default:
          //do nothing
          break;
      }
    }
  }

  private static void addToGroup(UpdatedFiles updatedFiles, HgChange change, String id) {
    updatedFiles.getGroupById(id).add(change.afterFile().getFile().getAbsolutePath(), HgVcs.VCS_NAME, null);
  }
}
