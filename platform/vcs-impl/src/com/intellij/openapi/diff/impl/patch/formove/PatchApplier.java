/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.diff.impl.patch.apply.ApplyTextFilePatch;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * for patches. for shelve.
 */
public class PatchApplier<BinaryType extends FilePatch> {
  private final Project myProject;
  private final VirtualFile myBaseDirectory;
  private final List<FilePatch> myPatches;
  private final CustomBinaryPatchApplier<BinaryType> myCustomForBinaries;
  private final LocalChangeList myTargetChangeList;

  private final List<FilePatch> myRemainingPatches;
  private final PathsVerifier<BinaryType> myVerifier;

  public PatchApplier(final Project project, final VirtualFile baseDirectory, final List<FilePatch> patches,
                      final LocalChangeList targetChangeList, final CustomBinaryPatchApplier<BinaryType> customForBinaries) {
    myProject = project;
    myBaseDirectory = baseDirectory;
    myPatches = patches;
    myTargetChangeList = targetChangeList;
    myCustomForBinaries = customForBinaries;
    myRemainingPatches = new ArrayList<FilePatch>();
    myVerifier = new PathsVerifier<BinaryType>(myProject, myBaseDirectory, myPatches, new PathsVerifier.BaseMapper() {
      @Nullable
      public VirtualFile getFile(FilePatch patch, String path) {
        return PathMerger.getFile(myBaseDirectory, path);
      }

      @Override
      public FilePath getPath(FilePatch patch, String path) {
        return PathMerger.getFile(new FilePathImpl(myBaseDirectory), path);
      }
    });
  }

  public ApplyPatchStatus execute() {
    return execute(true);
  }

  public ApplyPatchStatus execute(boolean showSuccessNotification) {
    myRemainingPatches.addAll(myPatches);

    final ApplyPatchStatus patchStatus = nonWriteActionPreCheck();
    if (ApplyPatchStatus.FAILURE.equals(patchStatus)) return patchStatus;

    final ApplyPatchStatus applyStatus = ApplicationManager.getApplication().runWriteAction(new Computable<ApplyPatchStatus>() {
      public ApplyPatchStatus compute() {
        final Ref<ApplyPatchStatus> refStatus = new Ref<ApplyPatchStatus>(ApplyPatchStatus.FAILURE);
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          public void run() {
            refStatus.set(executeWritable());
          }
        }, VcsBundle.message("patch.apply.command"), null);
        return refStatus.get();
      }
    });
    final ApplyPatchStatus status = ApplyPatchStatus.SUCCESS.equals(patchStatus) ? applyStatus :
                                    ApplyPatchStatus.and(patchStatus, applyStatus);
    // listeners finished, all 'legal' file additions/deletions with VCS are done
    final TriggerAdditionOrDeletion trigger = new TriggerAdditionOrDeletion(myProject);
    addSkippedItems(trigger);
    trigger.process();
    if(showSuccessNotification || !ApplyPatchStatus.SUCCESS.equals(status)) {
      showApplyStatus(myProject, status);
    }
    refreshFiles(trigger.getAffected());
    return status;
  }

  public static ApplyPatchStatus executePatchGroup(final Collection<PatchApplier> group) {
    if (group.isEmpty()) return ApplyPatchStatus.SUCCESS; //?
    final Project project = group.iterator().next().myProject;

    ApplyPatchStatus result = ApplyPatchStatus.SUCCESS;
    for (PatchApplier patchApplier : group) {
      result = ApplyPatchStatus.and(result, patchApplier.nonWriteActionPreCheck());
      if (ApplyPatchStatus.FAILURE.equals(result)) return result;
    }
    result = ApplyPatchStatus.and(result, ApplicationManager.getApplication().runWriteAction(new Computable<ApplyPatchStatus>() {
      public ApplyPatchStatus compute() {
        final Ref<ApplyPatchStatus> refStatus = new Ref<ApplyPatchStatus>(null);
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            for (PatchApplier applier : group) {
              refStatus.set(ApplyPatchStatus.and(refStatus.get(), applier.executeWritable()));
            }
          }
        }, VcsBundle.message("patch.apply.command"), null);
        return refStatus.get();
      }
    }));
    result = result == null ? ApplyPatchStatus.FAILURE : result;
    final TriggerAdditionOrDeletion trigger = new TriggerAdditionOrDeletion(project);
    for (PatchApplier applier : group) {
      applier.addSkippedItems(trigger);
    }
    trigger.process();

    for (PatchApplier applier : group) {
      applier.refreshFiles(trigger.getAffected());
    }
    showApplyStatus(project, result);
    return result;
  }

  protected void addSkippedItems(final TriggerAdditionOrDeletion trigger) {
    trigger.addExisting(myVerifier.getToBeAdded());
    trigger.addDeleted(myVerifier.getToBeDeleted());
  }

  public ApplyPatchStatus nonWriteActionPreCheck() {
    final boolean value = myVerifier.nonWriteActionPreCheck();
    if (! value) return ApplyPatchStatus.FAILURE;

    final List<FilePatch> skipped = myVerifier.getSkipped();
    final boolean applyAll = skipped.isEmpty();
    myPatches.removeAll(skipped);
    return applyAll ? ApplyPatchStatus.SUCCESS : ((skipped.size() == myPatches.size()) ? ApplyPatchStatus.ALREADY_APPLIED : ApplyPatchStatus.PARTIAL) ;
  }

  protected ApplyPatchStatus executeWritable() {
    return ApplicationManager.getApplication().runWriteAction(new Computable<ApplyPatchStatus>() {
      public ApplyPatchStatus compute() {
        final Ref<ApplyPatchStatus> refStatus = new Ref<ApplyPatchStatus>(ApplyPatchStatus.FAILURE);
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          public void run() {
            if (! myVerifier.execute()) {
              return;
            }

            if (! makeWritable(myVerifier.getWritableFiles())) {
              return;
            }

            final List<Pair<VirtualFile, ApplyTextFilePatch>> textPatches = myVerifier.getTextPatches();
            if (! fileTypesAreOk(textPatches)) {
              return;
            }

            try {
              markInternalOperation(textPatches, true);

              final ApplyPatchStatus status = actualApply(myVerifier);

              if (status != null) {
                refStatus.set(status);
              }
            }
            finally {
              markInternalOperation(textPatches, false);
            }
          } // end of Command run
        }, VcsBundle.message("patch.apply.command"), null);
        return refStatus.get();
      }
    });
  }

  private static void markInternalOperation(List<Pair<VirtualFile, ApplyTextFilePatch>> textPatches, boolean set) {
    for (Pair<VirtualFile, ApplyTextFilePatch> patch : textPatches) {
      ChangesUtil.markInternalOperation(patch.getFirst(), set);
    }
  }

  protected void refreshFiles(final Collection<FilePath> additionalDirectly) {
    final List<FilePath> directlyAffected = myVerifier.getDirectlyAffected();
    final List<VirtualFile> indirectlyAffected = myVerifier.getAllAffected();
    directlyAffected.addAll(additionalDirectly);

    final RefreshSession session = RefreshQueue.getInstance().createSession(false, true, new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;

        final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
        if ((myTargetChangeList != null) && (! directlyAffected.isEmpty()) &&
            (! myTargetChangeList.getName().equals(changeListManager.getDefaultListName()))) {
          changeListManager.invokeAfterUpdate(new FilesMover(changeListManager, directlyAffected), InvokeAfterUpdateMode.BACKGROUND_CANCELLABLE,
          VcsBundle.message("change.lists.manager.move.changes.to.list"),
          new Consumer<VcsDirtyScopeManager>() {
            public void consume(final VcsDirtyScopeManager vcsDirtyScopeManager) {
              vcsDirtyScopeManager.filePathsDirty(directlyAffected, null);
            }
          }, null);
        } else {
          final VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
          // will schedule update
          vcsDirtyScopeManager.filePathsDirty(directlyAffected, null);
        }
      }
    });
    session.addAllFiles(indirectlyAffected);
    session.launch();
  }

  @Nullable
  private ApplyPatchStatus actualApply(final PathsVerifier<BinaryType> verifier) {
    final List<Pair<VirtualFile, ApplyTextFilePatch>> textPatches = verifier.getTextPatches();
    final ApplyPatchContext context = new ApplyPatchContext(myBaseDirectory, 0, true, true);
    ApplyPatchStatus status = null;

    try {
      status = applyList(textPatches, context, status);

      if (myCustomForBinaries == null) {
        status = applyList(verifier.getBinaryPatches(), context, status);
      } else {
        final List<Pair<VirtualFile, ApplyFilePatchBase<BinaryType>>> binaryPatches = verifier.getBinaryPatches();
        ApplyPatchStatus patchStatus = myCustomForBinaries.apply(binaryPatches);
        final List<FilePatch> appliedPatches = myCustomForBinaries.getAppliedPatches();
        moveForCustomBinaries(binaryPatches, appliedPatches);

        status = ApplyPatchStatus.and(status, patchStatus);
        myRemainingPatches.removeAll(appliedPatches);
      }
    }
    catch (IOException e) {
      showError(myProject, e.getMessage(), true);
      return ApplyPatchStatus.FAILURE;
    }
    return status;
  }

  private void moveForCustomBinaries(final List<Pair<VirtualFile, ApplyFilePatchBase<BinaryType>>> patches,
                                     final List<FilePatch> appliedPatches) throws IOException {
    for (Pair<VirtualFile, ApplyFilePatchBase<BinaryType>> patch : patches) {
      if (appliedPatches.contains(patch.getSecond().getPatch())) {
        myVerifier.doMoveIfNeeded(patch.getFirst());
      }
    }
  }

  private <V extends FilePatch, T extends ApplyFilePatchBase<V>> ApplyPatchStatus applyList(final List<Pair<VirtualFile, T>> patches, final ApplyPatchContext context,
                                     ApplyPatchStatus status) throws IOException {
    for (Pair<VirtualFile, T> patch : patches) {
      ApplyPatchStatus patchStatus = ApplyPatchAction.applyOnly(myProject, patch.getSecond(), context, patch.getFirst());
      myVerifier.doMoveIfNeeded(patch.getFirst());

      status = ApplyPatchStatus.and(status, patchStatus);
      if (patchStatus != ApplyPatchStatus.FAILURE) {
        myRemainingPatches.remove(patch.getSecond().getPatch());
      } else {
        // interrupt if failure
        return status;
      }
    }
    return status;
  }

  protected static void showApplyStatus(final Project project, final ApplyPatchStatus status) {
    if (status == ApplyPatchStatus.ALREADY_APPLIED) {
      showError(project, VcsBundle.message("patch.apply.already.applied"), false);
    }
    else if (status == ApplyPatchStatus.PARTIAL) {
      showError(project, VcsBundle.message("patch.apply.partially.applied"), false);
    } else if (ApplyPatchStatus.SUCCESS.equals(status)) {
      ToolWindowManager.getInstance(project).notifyByBalloon(ChangesViewContentManager.TOOLWINDOW_ID, MessageType.INFO,
                                                               VcsBundle.message("patch.apply.success.applied.text"));
    }
  }

  public List<FilePatch> getRemainingPatches() {
    return myRemainingPatches;
  }

  public boolean hasRemainingPatches() {
    return ! myRemainingPatches.isEmpty();
  }

  private boolean makeWritable(final List<VirtualFile> filesToMakeWritable) {
    final VirtualFile[] fileArray = VfsUtil.toVirtualFileArray(filesToMakeWritable);
    final ReadonlyStatusHandler.OperationStatus readonlyStatus = ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(fileArray);
    return (! readonlyStatus.hasReadonlyFiles());
  }

  private boolean fileTypesAreOk(final List<Pair<VirtualFile, ApplyTextFilePatch>> textPatches) {
    for (Pair<VirtualFile, ApplyTextFilePatch> textPatch : textPatches) {
      final VirtualFile file = textPatch.getFirst();
      if (! file.isDirectory()) {
        FileType fileType = file.getFileType();
        if (fileType == FileTypes.UNKNOWN) {
          fileType = FileTypeChooser.associateFileType(file.getPresentableName());
          if (fileType == null) {
            showError(myProject, "Cannot apply patch. File " + file.getPresentableName() + " type not defined.", true);
            return false;
          }
        }
      }
    }
    return true;
  }

  public static void showError(final Project project, final String message, final boolean error) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return;
    }
    final String title = VcsBundle.message("patch.apply.dialog.title");
    final Runnable messageShower = new Runnable() {
      public void run() {
        if (error) {
          Messages.showErrorDialog(project, message, title);
        }
        else {
          Messages.showInfoMessage(project, message, title);
        }
      }
    };
    if (application.isDispatchThread()) {
      messageShower.run();
    } else {
      application.invokeLater(new Runnable() {
        public void run() {
          messageShower.run();
        }
      });
    }
  }

  private class FilesMover implements Runnable {
    private final ChangeListManager myChangeListManager;
    private final List<FilePath> myDirectlyAffected;

    public FilesMover(final ChangeListManager changeListManager, final List<FilePath> directlyAffected) {
      myChangeListManager = changeListManager;
      myDirectlyAffected = directlyAffected;
    }

    public void run() {
      List<Change> changes = new ArrayList<Change>();
      for(FilePath file: myDirectlyAffected) {
        final Change change = myChangeListManager.getChange(file);
        if (change != null) {
          changes.add(change);
        }
      }

      myChangeListManager.moveChangesTo(myTargetChangeList, changes.toArray(new Change[changes.size()]));
    }
  }
}
