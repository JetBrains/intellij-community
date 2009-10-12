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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * for patches. for shelve.
 */
public class PatchApplier {
  private final Project myProject;
  private final VirtualFile myBaseDirectory;
  private final List<FilePatch> myPatches;
  private final CustomBinaryPatchApplier myCustomForBinaries;
  private final LocalChangeList myTargetChangeList;

  private final List<FilePatch> myRemainingPatches;
  private final PathsVerifier myVerifier;

  public PatchApplier(final Project project, final VirtualFile baseDirectory, final List<FilePatch> patches,
                      final LocalChangeList targetChangeList, final CustomBinaryPatchApplier customForBinaries) {
    myProject = project;
    myBaseDirectory = baseDirectory;
    myPatches = patches;
    myTargetChangeList = targetChangeList;
    myCustomForBinaries = customForBinaries;
    myRemainingPatches = new ArrayList<FilePatch>();
    myVerifier = new PathsVerifier(myProject, myBaseDirectory, myPatches);
  }

  // todo progress
  public ApplyPatchStatus execute() {
    myRemainingPatches.addAll(myPatches);

    final Ref<ApplyPatchStatus> refStatus = new Ref<ApplyPatchStatus>(ApplyPatchStatus.FAILURE);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          public void run() {
            if (! myVerifier.execute()) {
              return;
            }

            if (! makeWritable(myVerifier.getWritableFiles())) {
              return;
            }

            final List<Pair<VirtualFile, FilePatch>> textPatches = myVerifier.getTextPatches();
            if (! fileTypesAreOk(textPatches)) {
              return;
            }

            final ApplyPatchStatus status = actualApply(myVerifier);

            if (status != null) {
              refStatus.set(status);
            }
          } // end of Command run
        }, VcsBundle.message("patch.apply.command"), null);
      }
    });
    showApplyStatus(refStatus.get());

    refreshFiles();

    return refStatus.get();
  }

  private void refreshFiles() {
    final List<FilePath> directlyAffected = myVerifier.getDirectlyAffected();
    final List<VirtualFile> indirectlyAffected = myVerifier.getAllAffected();

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
  private ApplyPatchStatus actualApply(final PathsVerifier verifier) {
    final List<Pair<VirtualFile, FilePatch>> textPatches = verifier.getTextPatches();
    final ApplyPatchContext context = new ApplyPatchContext(myBaseDirectory, 0, true, true);
    ApplyPatchStatus status = null;

    try {
      status = applyList(textPatches, context, status);

      if (myCustomForBinaries == null) {
        status = applyList(verifier.getBinaryPatches(), context, status);
      } else {
        final List<Pair<VirtualFile, FilePatch>> binaryPatches = verifier.getBinaryPatches();
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

  private void moveForCustomBinaries(final List<Pair<VirtualFile, FilePatch>> patches,
                                     final List<FilePatch> appliedPatches) throws IOException {
    for (Pair<VirtualFile, FilePatch> patch : patches) {
      if (appliedPatches.contains(patch.getSecond())) {
        myVerifier.doMoveIfNeeded(patch.getFirst());
      }
    }
  }

  private ApplyPatchStatus applyList(final List<Pair<VirtualFile, FilePatch>> patches, final ApplyPatchContext context,
                                     ApplyPatchStatus status) throws IOException {
    for (Pair<VirtualFile, FilePatch> patch : patches) {
      ApplyPatchStatus patchStatus = ApplyPatchAction.applyOnly(myProject, patch.getSecond(), context, patch.getFirst());
      myVerifier.doMoveIfNeeded(patch.getFirst());

      status = ApplyPatchStatus.and(status, patchStatus);
      if (patchStatus != ApplyPatchStatus.FAILURE) {
        myRemainingPatches.remove(patch.getSecond());
      } else {
        // interrupt if failure
        return status;
      }
    }
    return status;
  }

  private void showApplyStatus(final ApplyPatchStatus status) {
    if (status == ApplyPatchStatus.ALREADY_APPLIED) {
      showError(myProject, VcsBundle.message("patch.apply.already.applied"), false);
    }
    else if (status == ApplyPatchStatus.PARTIAL) {
      showError(myProject, VcsBundle.message("patch.apply.partially.applied"), false);
    } else if (ApplyPatchStatus.SUCCESS.equals(status)) {
      ToolWindowManager.getInstance(myProject).notifyByBalloon(ChangesViewContentManager.TOOLWINDOW_ID, MessageType.INFO,
                                                               VcsBundle.message("patch.apply.success.applied.text"));
    }
  }

  public List<FilePatch> getRemainingPatches() {
    return myRemainingPatches;
  }

  private boolean makeWritable(final List<VirtualFile> filesToMakeWritable) {
    final VirtualFile[] fileArray = filesToMakeWritable.toArray(new VirtualFile[filesToMakeWritable.size()]);
    final ReadonlyStatusHandler.OperationStatus readonlyStatus = ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(fileArray);
    return (! readonlyStatus.hasReadonlyFiles());
  }

  private boolean fileTypesAreOk(final List<Pair<VirtualFile, FilePatch>> textPatches) {
    for (Pair<VirtualFile, FilePatch> textPatch : textPatches) {
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

  @Nullable
  public static VirtualFile getFile(final VirtualFile baseDir, final String path) {
    if (path == null) {
      return null;
    }
    final List<String> tail = new ArrayList<String>();
    final VirtualFile file = getFile(baseDir, path, tail);
    if (tail.isEmpty()) {
      return file;
    }
    return null;
  }

  @Nullable
  public static VirtualFile getFile(final VirtualFile baseDir, final String path, final List<String> tail) {
    VirtualFile child = baseDir;

    final String[] pieces = RelativePathCalculator.split(path);

    for (int i = 0; i < pieces.length; i++) {
      final String piece = pieces[i];
      if (child == null) {
        return null;
      }
      if ("".equals(piece) || ".".equals(piece)) {
        continue;
      }
      if ("..".equals(piece)) {
        child = child.getParent();
        continue;
      }

      VirtualFile nextChild = child.findChild(piece);
      if (nextChild == null) {
        if (tail != null) {
          for (int j = i; j < pieces.length; j++) {
            final String pieceInner = pieces[j];
            tail.add(pieceInner);
          }
        }
        return child;
      }
      child = nextChild;
    }

    return child;
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
