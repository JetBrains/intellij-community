// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChange;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class VcsShelveUtils {
  private static final Logger LOG = Logger.getInstance(VcsShelveUtils.class.getName());

  public static void doSystemUnshelve(final Project project,
                                      final ShelvedChangeList shelvedChangeList,
                                      @Nullable final LocalChangeList targetChangeList,
                                      final ShelveChangesManager shelveManager,
                                      @Nullable final String leftConflictTitle,
                                      @Nullable final String rightConflictTitle) {
    VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    final String projectPath = baseDir.getPath() + "/";

    shelvedChangeList.loadChangesIfNeeded(project);
    final List<ShelvedChange> changes = Objects.requireNonNull(shelvedChangeList.getChanges());
    List<ShelvedBinaryFile> binaryFiles = shelvedChangeList.getBinaryFiles();

    LOG.info("refreshing files ");
    // The changes are temporary copied to the first local change list, the next operation will restore them back
    // Refresh files that might be affected by unshelve
    refreshFilesBeforeUnshelve(projectPath, changes, binaryFiles);

    LOG.info("Unshelving shelvedChangeList: " + shelvedChangeList);
    // we pass null as target change list for Patch Applier to do NOTHING with change lists
    shelveManager.unshelveChangeList(shelvedChangeList, changes, binaryFiles, targetChangeList, false, true,
                                     true, leftConflictTitle, rightConflictTitle, true);
    ApplicationManager.getApplication().invokeAndWait(() -> markUnshelvedFilesNonUndoable(project, changes));
  }

  @CalledInAwt
  private static void markUnshelvedFilesNonUndoable(@NotNull final Project project,
                                                    @NotNull List<? extends ShelvedChange> changes) {
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
                                                 @NotNull List<? extends ShelvedChange> shelvedChanges,
                                                 @NotNull List<? extends ShelvedBinaryFile> binaryFiles) {
    HashSet<File> filesToRefresh = new HashSet<>();
    shelvedChanges.forEach(c -> {
      if (c.getBeforePath() != null) {
        filesToRefresh.add(new File(projectPath + c.getBeforePath()));
      }
      if (c.getAfterPath() != null) {
        filesToRefresh.add(new File(projectPath + c.getAfterPath()));
      }
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
   * Shelve changes
   *
   *
   * @param project       the context project
   * @param shelveManager the shelve manager
   * @param changes       the changes to process
   * @param description   the description of for the shelve
   * @param exceptions    the generated exceptions
   * @param rollback
   * @return created shelved change list or null in case failure
   */
  @Nullable
  public static ShelvedChangeList shelveChanges(final Project project, final ShelveChangesManager shelveManager, Collection<? extends Change> changes,
                                                final String description,
                                                final List<? super VcsException> exceptions, boolean rollback, boolean markToBeDeleted) {
    try {
      ShelvedChangeList shelve = shelveManager.shelveChanges(changes, description, rollback, markToBeDeleted);
      BackgroundTaskUtil.syncPublisher(project, ShelveChangesManager.SHELF_TOPIC).stateChanged(new ChangeEvent(VcsShelveUtils.class));
      return shelve;
    }
    catch (IOException e) {
      exceptions.add(new VcsException("Shelving changes failed: " + description, e));
      return null;
    }
    catch (VcsException e) {
      exceptions.add(e);
      return null;
    }
  }
}
