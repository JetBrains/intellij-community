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
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.zmlx.hg4idea.HgErrorHandler.ensureSuccess;

public class HgRegularUpdater implements HgUpdater {

  private final Project project;
  @NotNull private final VirtualFile repository;
  @NotNull private final UpdateConfiguration updateConfiguration;
  private static final Logger LOG = Logger.getInstance(HgRegularUpdater.class);

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

  public boolean update(final UpdatedFiles updatedFiles, ProgressIndicator indicator, List<VcsException> warnings)
    throws VcsException {
    indicator.setText(HgVcsMessages.message("hg4idea.progress.updating", repository.getPath()));

    HgShowConfigCommand configCommand = new HgShowConfigCommand(project);
    String defaultPath = configCommand.getDefaultPath(repository);

    if (StringUtil.isEmptyOrSpaces(defaultPath)) {
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
      boolean pullResult = pull(repository, indicator);
      if (!pullResult) {
        return false;
      }
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

        HgCommandResult mergeResult = doMerge(updatedFiles, indicator, warnings, pulledBranchHeads.get(0));

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
    return true;
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

  private void updateToPulledHead(VirtualFile repo, UpdatedFiles updatedFiles, HgRevisionNumber newHead, ProgressIndicator indicator) {
    indicator.setText2(HgVcsMessages.message("hg4idea.update.progress.updating.to.pulled.head"));
    HgRevisionNumber parentBeforeUpdate = new HgWorkingCopyRevisionsCommand(project).firstParent(repo);
    HgUpdateCommand updateCommand = new HgUpdateCommand(project, repository);
    updateCommand.setRevision(newHead.getChangeset());
    updateCommand.setClean(true);
    updateCommand.execute();

    HgRevisionNumber commonParent = findCommonParent(newHead, parentBeforeUpdate);
    addUpdatedFiles(repo, updatedFiles, commonParent, newHead);
  }

  private @Nullable HgRevisionNumber findCommonParent(HgRevisionNumber newHead, HgRevisionNumber parentBeforeUpdate) {
    // hg log -r 0:source --prune dest --limit 1
    final List<HgRevisionNumber> pulledRevisions = new HgMergePreviewCommand(project, newHead, parentBeforeUpdate, 1).execute(repository);
    if (pulledRevisions == null || pulledRevisions.isEmpty()) {
      return null;
    }
    HgRevisionNumber pulledRevision = pulledRevisions.get(0);
    final List<HgRevisionNumber> parentRevisions = new HgWorkingCopyRevisionsCommand(project).getRevisions(repository, "parent", null, pulledRevision, true);
    if (parentRevisions.isEmpty()) {
      return null;
    }
    return parentRevisions.get(0);
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
    return mergeResult;
  }

  private void abortOnLocalChanges() throws VcsException {
    if (getLocalChanges().size() != 0) {
      throw new VcsException(HgVcsMessages.message("hg4idea.update.error.localchanges", repository.getPath()));
    }
  }

  private void resolvePossibleConflicts(final UpdatedFiles updatedFiles) {
    new HgConflictResolver(project, updatedFiles).resolve(repository);
  }

  private Set<HgChange> getLocalChanges() {
    HgStatusCommand statusCommand = new HgStatusCommand(project);
    statusCommand.setIncludeIgnored(false);
    statusCommand.setIncludeUnknown(false);
    return statusCommand.execute(repository);
  }

  private boolean pull(VirtualFile repo, ProgressIndicator indicator)
    throws VcsException {
    indicator.setText2(HgVcsMessages.message("hg4idea.progress.pull.with.update"));
    HgPullCommand hgPullCommand = new HgPullCommand(project, repo);
    final String defaultPath = new HgShowConfigCommand(project).getDefaultPath(repo);
    hgPullCommand.setSource(defaultPath);
    hgPullCommand.setUpdate(false);
    hgPullCommand.setRebase(false);
    return hgPullCommand.execute();
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
    if (!StringUtil.isEmptyOrSpaces(possibleWarning)) {
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
    if (parentAfterUpdate == null || parentBeforeUpdate == null) {
      return;
    }
    if (parentAfterUpdate.equals(parentBeforeUpdate)) { // nothing to update => returning not to capture local uncommitted changes
      return;
    }
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
