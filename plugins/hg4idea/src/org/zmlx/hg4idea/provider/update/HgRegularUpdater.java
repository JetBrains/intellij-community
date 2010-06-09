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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.zmlx.hg4idea.HgErrorHandler.ensureSuccess;

public class HgRegularUpdater implements HgUpdater {

  private final Project project;
  @NotNull private final VirtualFile repository;
  @NotNull private final UpdateConfiguration updateConfiguration;

  public HgRegularUpdater(Project project, @NotNull VirtualFile repository, @NotNull UpdateConfiguration configuration) {
    this.project = project;
    this.repository = repository;
    this.updateConfiguration = configuration;
  }

  private boolean shouldPull() {
    return updateConfiguration.shouldPull();
  }

  private boolean shouldUpdate() {
    return updateConfiguration.shouldUpdate();
  }

  private boolean shouldMerge() {
    return updateConfiguration.shouldMerge();
  }

  private boolean shouldCommitAfterMerge() {
    return updateConfiguration.shouldCommitAfterMerge();
  }

  public void update(final UpdatedFiles updatedFiles, ProgressIndicator indicator, List<VcsException> warnings)
    throws VcsException {
    indicator.setText(HgVcsMessages.message("hg4idea.progress.updating", repository.getPath()));

    HgShowConfigCommand configCommand = new HgShowConfigCommand(project);
    String defaultPath = configCommand.getDefaultPath(repository);

    if (StringUtils.isBlank(defaultPath)) {
      throw new VcsException(HgVcsMessages.message("hg4idea.warning.no-default-update-path", repository.getPath()));
    }


    List<HgRevisionNumber> branchHeadsBeforePull = new HgHeadsCommand(project, repository).execute();

    if (branchHeadsBeforePull.size() > 1) {
      reportWarning(warnings, HgVcsMessages.message("hg4idea.update.warning.multipleHeadsBeforeUpdate", repository.getPath()));
    }

    //TODO perhaps report a warning in this case ?
//    //if the parent of the working dir is not the tip of the current branch, the user has
//    //manually brought his working copy to some specific revision. In that case we won't touch
//    //his setup
//    if (!parentRevision.equals(currentBranchHead)) {
//      throw new VcsException("working dir not at branch tip (use \"Update to...\" to check out branch tip)");
//    }

    if (shouldPull()) {
      pull(repository, indicator);
    }

    if (shouldUpdate()) {

      List<HgRevisionNumber> parentsBeforeUpdate = new HgWorkingCopyRevisionsCommand(project).parents(repository);
      if (parentsBeforeUpdate.size() > 1) {
        throw new VcsException(HgVcsMessages.message("hg4idea.update.error.uncommittedMerge", repository.getPath()));
      }

      indicator.setText2(HgVcsMessages.message("hg4idea.progress.countingHeads"));

      List<HgRevisionNumber> branchHeadsAfterPull = new HgHeadsCommand(project, repository).execute();
      List<HgRevisionNumber> pulledBranchHeads = determinePulledBranchHeads(branchHeadsBeforePull, branchHeadsAfterPull);
      List<HgRevisionNumber> remainingOriginalBranchHeads = determingRemainingOriginalBranchHeads(branchHeadsBeforePull, branchHeadsAfterPull);

      if (branchHeadsAfterPull.size() > 1 && shouldMerge()) {
        abortOnLocalChanges();
        abortOnMultiplePulledHeads(pulledBranchHeads);
        abortOnMultipleLocalHeads(remainingOriginalBranchHeads);

        //update to the pulled in head, because we consider that head as the 'authoritative' head
        updateToPulledHead(pulledBranchHeads.get(0), indicator);

        HgCommandResult mergeResult = doMerge(updatedFiles, indicator, warnings, remainingOriginalBranchHeads.get(0));

        if (shouldCommitAfterMerge()) {
          commitOrWarnAboutConflicts(warnings, mergeResult);
        }
      } else {
        //in case of multiple heads the update will report the appropriate error
        update(repository, indicator, updatedFiles, warnings);
      }

      //any kind of update could have resulted in merges and merge conflicts, so run the resolver
      resolvePossibleConflicts(updatedFiles);
    }
  }

  private List<HgRevisionNumber> determingRemainingOriginalBranchHeads(List<HgRevisionNumber> branchHeadsBeforePull, List<HgRevisionNumber> branchHeadsAfterPull) {
    List<HgRevisionNumber> originalBranchHeadsRemaining = new ArrayList<HgRevisionNumber>();
    for (HgRevisionNumber headAfterPull : branchHeadsAfterPull) {
      if (branchHeadsBeforePull.contains(headAfterPull)) {
        originalBranchHeadsRemaining.add(headAfterPull);
      }
    }
    return originalBranchHeadsRemaining;
  }

  private List<HgRevisionNumber> determinePulledBranchHeads(List<HgRevisionNumber> branchHeadsBeforePull, List<HgRevisionNumber> branchHeadsAfterPull) {
    List<HgRevisionNumber> pulledBranchHeads = new ArrayList<HgRevisionNumber>(branchHeadsAfterPull);
    pulledBranchHeads.removeAll(branchHeadsBeforePull);
    return pulledBranchHeads;
  }

  private void abortOnMultipleLocalHeads(List<HgRevisionNumber> originalBranchHeadsRemaining) throws VcsException {
    if (originalBranchHeadsRemaining.size() != 1) {
      throw new VcsException(HgVcsMessages.message("hg4idea.update.error.merge.multipleLocalHeads", repository.getPath()));
    }
  }

  private void abortOnMultiplePulledHeads(List<HgRevisionNumber> newBranchHeadsAfterPull) throws VcsException {
    if (newBranchHeadsAfterPull.size() != 1) {
      throw new VcsException(HgVcsMessages.message("hg4idea.update.error.merge.multipleRemoteHeads", newBranchHeadsAfterPull.size(), repository.getPath()));
    }
  }

  private void updateToPulledHead(HgRevisionNumber newHead, ProgressIndicator indicator) {
    indicator.setText2(HgVcsMessages.message("hg4idea.update.progress.updating.to.pulled.head"));
    HgUpdateCommand updateCommand = new HgUpdateCommand(project, repository);
    updateCommand.setRevision(newHead.getChangeset());
    updateCommand.setClean(true);
    updateCommand.execute();
  }

  private void commitOrWarnAboutConflicts(List<VcsException> exceptions, HgCommandResult mergeResult) throws VcsException {
    if (mergeResult.getExitValue() == 0) { //operation successful and no conflicts
      try {
        new HgCommitCommand(project, repository, "Automated merge").execute();
      } catch (HgCommandException e) {
        throw new VcsException(e);
      }
    } else {
      reportWarning(exceptions, HgVcsMessages.message("hg4idea.update.warning.merge.conflicts", repository.getPath()));
    }
  }

  private HgCommandResult doMerge(UpdatedFiles updatedFiles, ProgressIndicator indicator, List<VcsException> exceptions, HgRevisionNumber headToMerge) throws VcsException {
    indicator.setText2(HgVcsMessages.message("hg4idea.update.progress.merging"));
    HgMergeCommand mergeCommand = new HgMergeCommand(project, repository);
    //do not explicitly set the revision, that way mercurial itself checks that there are exactly
    //two heads in this branch
//    mergeCommand.setRevision(headToMerge.getRevision());
    HgCommandResult mergeResult = new HgHeadMerger(project, mergeCommand).merge(repository, updatedFiles, headToMerge);
    handlePossibleWarning(exceptions, mergeResult.getWarnings());
    return mergeResult;
  }

  private void abortOnLocalChanges() throws VcsException {
    if (getLocalChanges().size() != 0) {
      throw new VcsException(HgVcsMessages.message("hg4idea.update.error.localchanges", repository.getPath()));
    }
  }

  private void resolvePossibleConflicts(final UpdatedFiles updatedFiles) {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        new HgConflictResolver(project, updatedFiles).resolve(repository);
      }
    }, ModalityState.defaultModalityState());
  }

  private Set<HgChange> getLocalChanges() {
    HgStatusCommand statusCommand = new HgStatusCommand(project);
    statusCommand.setIncludeIgnored(false);
    statusCommand.setIncludeUnknown(false);
    return statusCommand.execute(repository);
  }

  private void pull(VirtualFile repo, ProgressIndicator indicator)
    throws VcsException {
    indicator.setText2(HgVcsMessages.message("hg4idea.progress.pull.with.update"));
    HgPullCommand hgPullCommand = new HgPullCommand(project, repo);
    hgPullCommand.setSource(new HgShowConfigCommand(project).getDefaultPath(repo));
    hgPullCommand.setUpdate(false);
    hgPullCommand.setRebase(false);
    ensureSuccess(hgPullCommand.execute());
  }

  private void update(@NotNull VirtualFile repo, ProgressIndicator indicator, UpdatedFiles updatedFiles, List<VcsException> warnings) throws VcsException {
    indicator.setText2(HgVcsMessages.message("hg4idea.progress.updatingworkingdir"));

    HgRevisionNumber parentBeforeUpdate = new HgWorkingCopyRevisionsCommand(project).firstParent(repo);
    HgUpdateCommand hgUpdateCommand = new HgUpdateCommand(project, repo);
    String warningMessages = ensureSuccess(hgUpdateCommand.execute()).getWarnings();
    handlePossibleWarning(warnings, warningMessages);

    HgRevisionNumber parentAfterUpdate = new HgWorkingCopyRevisionsCommand(project).firstParent(repo);

    addUpdatedFiles(repo, updatedFiles, parentBeforeUpdate, parentAfterUpdate);
  }

  private void handlePossibleWarning(List<VcsException> exceptions, String possibleWarning) {
    if (!StringUtils.isBlank(possibleWarning)) {
      reportWarning(exceptions, possibleWarning);
    }
  }

  private void reportWarning(List<VcsException> exceptions, String warningMessage) {
    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    VcsException warningException = new VcsException(warningMessage);
    warningException.setIsWarning(true);
    exceptions.add(warningException);
  }

  private void addUpdatedFiles(VirtualFile repo, UpdatedFiles updatedFiles, HgRevisionNumber parentBeforeUpdate, HgRevisionNumber parentAfterUpdate) {
    HgStatusCommand statusCommand = new HgStatusCommand(project);
    statusCommand.setBaseRevision(parentBeforeUpdate);
    statusCommand.setTargetRevision(parentAfterUpdate);
    Set<HgChange> changes = statusCommand.execute(repo);
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
