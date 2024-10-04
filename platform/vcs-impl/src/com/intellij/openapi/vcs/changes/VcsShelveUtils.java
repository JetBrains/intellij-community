// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChange;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public final class VcsShelveUtils {
  private static final Logger LOG = Logger.getInstance(VcsShelveUtils.class.getName());

  @NotNull
  public static ApplyPatchStatus doSystemUnshelve(final Project project,
                                                  final ShelvedChangeList shelvedChangeList,
                                                  @Nullable final LocalChangeList targetChangeList,
                                                  final ShelveChangesManager shelveManager,
                                                  @NlsContexts.Label @Nullable final String leftConflictTitle,
                                                  @NlsContexts.Label @Nullable final String rightConflictTitle,
                                                  boolean reportLocalHistoryActivity) {
    VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    final String projectPath = baseDir.getPath() + "/";

    shelvedChangeList.loadChangesIfNeeded(project);
    final List<ShelvedChange> changes = Objects.requireNonNull(shelvedChangeList.getChanges());
    List<ShelvedBinaryFile> binaryFiles = shelvedChangeList.getBinaryFiles();

    LOG.info("refreshing files ");
    // The changes are temporarily copied to the first local change list, the next operation will restore them back
    // Refresh files that might be affected by unshelve
    refreshFilesBeforeUnshelve(projectPath, changes, binaryFiles);

    LOG.info("Unshelving shelvedChangeList: " + shelvedChangeList + " into " + targetChangeList);
    ApplyPatchStatus status = shelveManager.unshelveChangeList(shelvedChangeList, changes, binaryFiles, targetChangeList, false, true,
                                                               true, leftConflictTitle, rightConflictTitle, true,
                                                               reportLocalHistoryActivity);
    ApplicationManager.getApplication().invokeAndWait(() -> markUnshelvedFilesNonUndoable(project, changes));
    return status;
  }

  @RequiresEdt
  private static void markUnshelvedFilesNonUndoable(@NotNull final Project project,
                                                    @NotNull List<ShelvedChange> changes) {
    final UndoManagerImpl undoManager = (UndoManagerImpl)UndoManager.getInstance(project);
    if (undoManager != null && !changes.isEmpty()) {
      ContainerUtil.process(changes, change -> {
        final VirtualFile vfUnderProject = VfsUtil.findFileByIoFile(new File(project.getBasePath(), change.getAfterPath()), false);
        if (vfUnderProject != null) {
          final DocumentReference documentReference = DocumentReferenceManager.getInstance().create(vfUnderProject);
          undoManager.nonundoableActionPerformed(documentReference, false);
          undoManager.invalidateActionsFor(documentReference);
        }
        return true;
      });
    }
  }

  private static void refreshFilesBeforeUnshelve(String projectPath,
                                                 @NotNull List<ShelvedChange> shelvedChanges,
                                                 @NotNull List<ShelvedBinaryFile> binaryFiles) {
    HashSet<File> filesToRefresh = new HashSet<>();
    shelvedChanges.forEach(c -> {
      filesToRefresh.add(new File(projectPath + c.getBeforePath()));
      filesToRefresh.add(new File(projectPath + c.getAfterPath()));
    });
    binaryFiles.forEach(f -> {
      if (f.BEFORE_PATH != null) {
        filesToRefresh.add(new File(projectPath + f.BEFORE_PATH));
      }
      if (f.AFTER_PATH != null) {
        filesToRefresh.add(new File(projectPath + f.AFTER_PATH));
      }
    });
    LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh);
  }

  /**
   * @param project     the context project
   * @param changes     the changes to process
   * @param description the description of for the shelve
   * @return created shelved change list or null in case failure
   */
  @Nullable
  public static ShelvedChangeList shelveChanges(final Project project,
                                                Collection<? extends Change> changes,
                                                final @Nls String description,
                                                boolean rollback,
                                                boolean markToBeDeleted) throws VcsException {
    try {
      return ShelveChangesManager.getInstance(project).shelveChanges(changes, description, rollback, markToBeDeleted);
    }
    catch (IOException e) {
      throw new VcsException(VcsBundle.message("changes.error.shelving.changes.failed", description), e);
    }
  }
}
