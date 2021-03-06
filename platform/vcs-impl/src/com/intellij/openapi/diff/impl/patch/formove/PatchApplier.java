// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchUtil;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.diff.impl.patch.formove.PathsVerifier.PatchAndFile;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vcs.impl.PartialChangesUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsImplUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.progress.ProgressManager.progress;
import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.*;
import static com.intellij.util.ObjectUtils.chooseNotNull;

/**
 * for patches. for shelve.
 */
public final class PatchApplier {
  private static final Logger LOG = Logger.getInstance(PatchApplier.class);
  private final Project myProject;
  private final VirtualFile myBaseDirectory;
  @NotNull private final List<? extends FilePatch> myPatches;
  private final CommitContext myCommitContext;
  @Nullable private final LocalChangeList myTargetChangeList;
  @NotNull private final List<FilePatch> myRemainingPatches;
  @NotNull private final List<FilePatch> myFailedPatches;
  private final PathsVerifier myVerifier;

  private final boolean myReverseConflict;
  @NlsContexts.Label @Nullable private final String myLeftConflictPanelTitle;
  @NlsContexts.Label @Nullable private final String myRightConflictPanelTitle;

  public PatchApplier(@NotNull Project project,
                      @NotNull VirtualFile baseDirectory,
                      @NotNull List<? extends FilePatch> patches,
                      @Nullable LocalChangeList targetChangeList,
                      @Nullable CommitContext commitContext,
                      boolean reverseConflict,
                      @NlsContexts.Label @Nullable String leftConflictPanelTitle,
                      @NlsContexts.Label @Nullable String rightConflictPanelTitle) {
    myProject = project;
    myBaseDirectory = baseDirectory;
    myPatches = patches;
    myTargetChangeList = targetChangeList;
    myCommitContext = commitContext;
    myReverseConflict = reverseConflict;
    myLeftConflictPanelTitle = leftConflictPanelTitle;
    myRightConflictPanelTitle = rightConflictPanelTitle;
    myRemainingPatches = new ArrayList<>();
    myFailedPatches = new ArrayList<>();
    myVerifier = new PathsVerifier(myProject, baseDirectory, myPatches);
  }

  public void setIgnoreContentRootsCheck() {
    myVerifier.setIgnoreContentRootsCheck(true);
  }

  public PatchApplier(@NotNull Project project,
                      @NotNull VirtualFile baseDirectory,
                      @NotNull List<? extends FilePatch> patches,
                      @Nullable LocalChangeList targetChangeList,
                      @Nullable CommitContext commitContext) {
    this(project, baseDirectory, patches, targetChangeList, commitContext, false, null, null);
  }

  @NotNull
  public List<FilePatch> getRemainingPatches() {
    return myRemainingPatches;
  }

  @NotNull
  private Collection<FilePatch> getFailedPatches() {
    return myFailedPatches;
  }

  @NotNull
  private List<FilePatch> getBinaryPatches() {
    return ContainerUtil.mapNotNull(myVerifier.getBinaryPatches(),
                                    patchInfo -> patchInfo.getApplyPatch().getPatch());
  }

  public void execute() {
    execute(true, false);
  }

  public ApplyPatchStatus execute(boolean showSuccessNotification, boolean silentAddDelete) {
    return executePatchGroup(Collections.singletonList(this), myTargetChangeList, showSuccessNotification, silentAddDelete);
  }

  private static void runWithDefaultConfirmations(@NotNull Project project, boolean resetConfirmations, @NotNull Runnable task) {
    if (!resetConfirmations) {
      task.run();
    }
    else {
      ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
      VcsShowConfirmationOption addConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, null);
      VcsShowConfirmationOption deleteConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, null);

      VcsShowConfirmationOption.Value addConfirmationValue = addConfirmation.getValue();
      VcsShowConfirmationOption.Value deleteConfirmationValue = deleteConfirmation.getValue();
      addConfirmation.setValue(VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
      deleteConfirmation.setValue(VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
      try {
        task.run();
      }
      finally {
        addConfirmation.setValue(addConfirmationValue);
        deleteConfirmation.setValue(deleteConfirmationValue);
      }
    }
  }

  public static ApplyPatchStatus executePatchGroup(final Collection<PatchApplier> group, @Nullable LocalChangeList localChangeList) {
    return executePatchGroup(group, localChangeList, true, false);
  }

  /**
   * Pass 'null' {@code targetChangeList} if changelist doesn't matter, changes will be applied as-it into default changelist.
   * If default changelist is changed before refresh, a race is possible that will put some of applied changes into the new default changelist.
   * <p>
   * If {@code targetChangeList} is specified, method will need to synchronously await for CLM refresh.
   * In this case, current thread MUST NOT be EDT or hold ReadLock, to prevent deadlock with VFS refresh.
   */
  private static ApplyPatchStatus executePatchGroup(@NotNull Collection<PatchApplier> group,
                                                    @Nullable LocalChangeList targetChangeList,
                                                    boolean showSuccessNotification,
                                                    boolean silentAddDelete) {
    if (group.isEmpty()) {
      return ApplyPatchStatus.SUCCESS; //?
    }

    Project project = group.iterator().next().myProject;
    return PartialChangesUtil.computeUnderChangeListSync(project, targetChangeList, () -> {
      ApplyPatchStatus result = ApplyPatchStatus.SUCCESS;
      for (PatchApplier patchApplier : group) {
        result = ApplyPatchStatus.and(result, patchApplier.nonWriteActionPreCheck());
      }

      final Label beforeLabel = LocalHistory.getInstance().putSystemLabel(project, VcsBundle.message("patch.apply.before.patch.label.text"));
      final TriggerAdditionOrDeletion trigger = new TriggerAdditionOrDeletion(project);

      final Ref<ApplyPatchStatus> refStatus = new Ref<>(result);
      ApplicationManager.getApplication().invokeAndWait(() -> {
        try {
          runWithDefaultConfirmations(project, silentAddDelete, () -> {
            CommandProcessor.getInstance().executeCommand(project, () -> {
              List<FilePath> toBeAdded = new ArrayList<>();
              List<FilePath> toBeDeleted = new ArrayList<>();
              for (PatchApplier applier : group) {
                refStatus.set(ApplyPatchStatus.and(refStatus.get(), applier.createFiles()));
                toBeAdded.addAll(applier.myVerifier.getToBeAdded());
                toBeDeleted.addAll(applier.myVerifier.getToBeDeleted());
              }
              trigger.prepare(toBeAdded, toBeDeleted);
              if (refStatus.get() == ApplyPatchStatus.SUCCESS) {
                // all pre-check results are valuable only if not successful; actual status we can receive after executeWritable
                refStatus.set(null);
              }
              for (PatchApplier applier : group) {
                refStatus.set(ApplyPatchStatus.and(refStatus.get(), applier.executeWritable()));
                if (refStatus.get() == ApplyPatchStatus.ABORT) {
                  break;
                }
              }
            }, VcsBundle.message("patch.apply.command"), null);
          });
        }
        finally {
          trigger.cleanup();
          LocalHistory.getInstance().putSystemLabel(project, VcsBundle.message("patch.apply.after.patch.label.text"));
        }
      });
      result = refStatus.get();
      result = result == null ? ApplyPatchStatus.FAILURE : result;

      trigger.processIt();

      AtomicBoolean doRollback = new AtomicBoolean();
      if (result == ApplyPatchStatus.FAILURE) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          doRollback.set(askToRollback(project, group));
        });
      }
      if (result == ApplyPatchStatus.ABORT || doRollback.get()) {
        rollbackUnderProgressIfNeeded(project, beforeLabel);
      }

      if (showSuccessNotification || !ApplyPatchStatus.SUCCESS.equals(result)) {
        showApplyStatus(project, result);
      }

      Set<FilePath> directlyAffected = new HashSet<>();
      Set<VirtualFile> indirectlyAffected = new HashSet<>();
      for (PatchApplier applier : group) {
        directlyAffected.addAll(applier.getDirectlyAffected());
        indirectlyAffected.addAll(applier.getIndirectlyAffected());
      }
      directlyAffected.addAll(trigger.getAffected());
      refreshPassedFiles(project, directlyAffected, indirectlyAffected);

      return result;
    });
  }

  @RequiresEdt
  private static boolean askToRollback(@NotNull Project project, @NotNull Collection<PatchApplier> group) {
    Collection<FilePatch> allFailed = ContainerUtil.concat(group, PatchApplier::getFailedPatches);
    boolean shouldInformAboutBinaries = ContainerUtil.exists(group, applier -> !applier.getBinaryPatches().isEmpty());
    List<FilePath> filePaths =
      ContainerUtil.map(allFailed, filePatch -> VcsUtil.getFilePath(chooseNotNull(filePatch.getAfterName(), filePatch.getBeforeName())));

    final UndoApplyPatchDialog undoApplyPatchDialog = new UndoApplyPatchDialog(project, filePaths, shouldInformAboutBinaries);
    return undoApplyPatchDialog.showAndGet();
  }

  private static void rollbackUnderProgressIfNeeded(@NotNull final Project project, @NotNull final Label labelToRevert) {
    Runnable rollback = () -> {
      try {
        labelToRevert.revert(project, project.getBaseDir());
        VcsNotifier.getInstance(project)
          .notifyImportantWarning(PATCH_APPLY_ABORTED,
                                  VcsBundle.message("patch.apply.aborted.title"),
                                  VcsBundle.message("patch.apply.aborted.message"));
      }
      catch (LocalHistoryException e) {
        VcsNotifier.getInstance(project)
          .notifyImportantWarning(PATCH_APPLY_ROLLBACK_FAILED,
                                  VcsBundle.message("patch.apply.rollback.failed.title"),
                                  VcsBundle.message("patch.apply.rollback.failed.message"));
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(rollback, VcsBundle.message("patch.apply.rollback.progress.title"), true, project);
    }
    else {
      progress(VcsBundle.message("patch.apply.rollback.progress"));
      rollback.run();
    }
  }


  @NotNull
  private ApplyPatchStatus nonWriteActionPreCheck() {
    final List<FilePatch> failedPreCheck = myVerifier.nonWriteActionPreCheck();
    final List<FilePatch> skipped = myVerifier.getSkipped();

    myRemainingPatches.addAll(myPatches);
    myFailedPatches.addAll(failedPreCheck);
    myPatches.removeAll(failedPreCheck);
    myPatches.removeAll(skipped);

    if (!failedPreCheck.isEmpty()) {
      return ApplyPatchStatus.FAILURE;
    }
    else if (skipped.isEmpty()) {
      return ApplyPatchStatus.SUCCESS;
    }
    else if (skipped.size() == myPatches.size()) {
      return ApplyPatchStatus.ALREADY_APPLIED;
    }
    else {
      return ApplyPatchStatus.PARTIAL;
    }
  }

  @Nullable
  private ApplyPatchStatus executeWritable() {
    ReadonlyStatusHandler.OperationStatus readOnlyFilesStatus =
      ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(myVerifier.getWritableFiles());
    if (readOnlyFilesStatus.hasReadonlyFiles()) {
      showError(myProject, readOnlyFilesStatus.getReadonlyFilesMessage());
      return ApplyPatchStatus.ABORT;
    }

    myFailedPatches.addAll(myVerifier.filterBadFileTypePatches());
    ApplyPatchStatus result = myFailedPatches.isEmpty() ? null : ApplyPatchStatus.FAILURE;
    List<PatchAndFile> textPatches = myVerifier.getTextPatches();
    try {
      markInternalOperation(textPatches, true);
      return ApplyPatchStatus.and(result, actualApply(textPatches, myVerifier.getBinaryPatches(), myCommitContext));
    }
    finally {
      markInternalOperation(textPatches, false);
    }
  }

  private @NotNull ApplyPatchStatus createFiles() {
    Boolean isSuccess = ApplicationManager.getApplication().runWriteAction((Computable<Boolean>)() -> {
      List<FilePatch> filePatches = myVerifier.execute();
      myFailedPatches.addAll(filePatches);
      myPatches.removeAll(filePatches);
      return myFailedPatches.isEmpty();
    });
    return isSuccess ? ApplyPatchStatus.SUCCESS : ApplyPatchStatus.FAILURE;
  }

  private static void markInternalOperation(List<PatchAndFile> textPatches, boolean set) {
    for (PatchAndFile patch : textPatches) {
      ChangesUtil.markInternalOperation(patch.getFile(), set);
    }
  }

  private List<FilePath> getDirectlyAffected() {
    return myVerifier.getDirectlyAffected();
  }

  private List<VirtualFile> getIndirectlyAffected() {
    return myVerifier.getAllAffected();
  }

  private static void refreshPassedFiles(@NotNull Project project,
                                         @NotNull Collection<? extends FilePath> directlyAffected,
                                         @NotNull Collection<? extends VirtualFile> indirectlyAffected) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    for (FilePath filePath : directlyAffected) {
      lfs.refreshAndFindFileByIoFile(filePath.getIOFile());
    }
    if (project.isDisposed()) return;

    VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
    vcsDirtyScopeManager.filePathsDirty(directlyAffected, null);
    vcsDirtyScopeManager.filesDirty(indirectlyAffected, null);
  }

  private @Nullable ApplyPatchStatus actualApply(@NotNull List<PatchAndFile> textPatches,
                                                @NotNull List<PatchAndFile> binaryPatches,
                                                @Nullable CommitContext commitContext) {
    ApplyPatchContext context = new ApplyPatchContext(myBaseDirectory, 0, true, true);
    try {
      ApplyPatchStatus status = applyList(textPatches, context, null, commitContext);
      return status == ApplyPatchStatus.ABORT ? status : applyList(binaryPatches, context, status, commitContext);
    }
    catch (IOException e) {
      showError(myProject, e.getMessage());
      return ApplyPatchStatus.ABORT;
    }
  }

  private @Nullable ApplyPatchStatus applyList(@NotNull List<PatchAndFile> patches,
                                              @NotNull ApplyPatchContext context,
                                              @Nullable ApplyPatchStatus status,
                                              @Nullable CommitContext commitContext) throws IOException {
    for (PatchAndFile patch : patches) {
      ApplyFilePatchBase<?> applyFilePatch = patch.getApplyPatch();
      ApplyPatchStatus patchStatus = ApplyPatchAction.applyContent(myProject, applyFilePatch, context, patch.getFile(), commitContext,
                                                                   myReverseConflict, myLeftConflictPanelTitle, myRightConflictPanelTitle);
      if (patchStatus == ApplyPatchStatus.SUCCESS || patchStatus == ApplyPatchStatus.ALREADY_APPLIED) {
        applyAdditionalPatchData(patch.getFile(), applyFilePatch.getPatch());
      }
      if (patchStatus == ApplyPatchStatus.ABORT) {
        return patchStatus;
      }

      status = ApplyPatchStatus.and(status, patchStatus);
      if (patchStatus == ApplyPatchStatus.FAILURE) {
        myFailedPatches.add(applyFilePatch.getPatch());
        continue;
      }
      if (patchStatus != ApplyPatchStatus.SKIP) {
        myVerifier.doMoveIfNeeded(patch.getFile());
        myRemainingPatches.remove(applyFilePatch.getPatch());
      }
    }
    return status;
  }

  private static <V extends FilePatch> void applyAdditionalPatchData(@NotNull VirtualFile fileToApplyData, @NotNull V filePatch) {
    int newFileMode = filePatch.getNewFileMode();
    File file = VfsUtilCore.virtualToIoFile(fileToApplyData);
    if (newFileMode == PatchUtil.EXECUTABLE_FILE_MODE || newFileMode == PatchUtil.REGULAR_FILE_MODE) {
      try {
        //noinspection ResultOfMethodCallIgnored
        file.setExecutable(newFileMode == PatchUtil.EXECUTABLE_FILE_MODE);
      }
      catch (Exception e) {
        LOG.warn("Can't change file mode for " + fileToApplyData.getPresentableName());
      }
    }
  }

  private static void showApplyStatus(@NotNull Project project, final ApplyPatchStatus status) {
    VcsNotifier vcsNotifier = VcsNotifier.getInstance(project);
    if (status == ApplyPatchStatus.ALREADY_APPLIED) {
      vcsNotifier.notifyMinorInfo(PATCH_ALREADY_APPLIED, VcsBundle.message("patch.apply.notification.title"), VcsBundle.message("patch.apply.already.applied"));
    }
    else if (status == ApplyPatchStatus.PARTIAL) {
      vcsNotifier.notifyMinorInfo(PATCH_PARTIALLY_APPLIED, VcsBundle.message("patch.apply.notification.title"), VcsBundle.message("patch.apply.partially.applied"));
    }
    else if (status == ApplyPatchStatus.SUCCESS) {
      vcsNotifier.notifySuccess(PATCH_APPLY_SUCCESS, "",
                                VcsBundle.message("patch.apply.success.applied.text"));
    }
  }

  public static void showError(final Project project, final @NlsContexts.DialogMessage String message) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    VcsImplUtil.showErrorMessage(project, message, VcsBundle.message("patch.apply.dialog.title"));
  }
}
