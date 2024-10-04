// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.PATCH_APPLY_NEW_FILES_ERROR;
import static com.intellij.util.Functions.identity;
import static com.intellij.vcsUtil.VcsUtil.groupByRoots;
import static java.util.Objects.requireNonNull;

@ApiStatus.Internal
public final class TriggerAdditionOrDeletion {
  private static final Logger LOG = Logger.getInstance(TriggerAdditionOrDeletion.class);

  private final Project myProject;
  private final VcsFileListenerContextHelper myVcsFileListenerContextHelper;

  private final Set<FilePath> myAffected = new HashSet<>();

  private final Map<AbstractVcs, Set<FilePath>> myPreparedAddition = new HashMap<>();
  private final Map<AbstractVcs, Set<FilePath>> myPreparedDeletion = new HashMap<>();

  public TriggerAdditionOrDeletion(@NotNull Project project) {
    myProject = project;
    myVcsFileListenerContextHelper = VcsFileListenerContextHelper.getInstance(myProject);
  }

  public Set<FilePath> getAffected() {
    return myAffected;
  }

  /**
   * Notify that files should be added/deleted in VCS.
   * <p>
   * Should be called in the same command as file modifications. Typically - BEFORE the actual file modification.
   * See {@link VcsFileListenerContextHelper} javadoc for exact constraints on order of events.
   */
  public void prepare(@NotNull Collection<? extends FilePath> toBeAdded,
                      @NotNull Collection<? extends FilePath> toBeDeleted) {
    processAddition(toBeAdded);
    processDeletion(toBeDeleted);
  }

  /**
   * Should be called on EDT after the command is finished.
   */
  public void cleanup() {
    myVcsFileListenerContextHelper.clearContext();
  }

  /**
   * Called on pooled thread when all operations are completed.
   */
  public void processIt() {
    final List<FilePath> incorrectFilePath = new ArrayList<>();

    for (Map.Entry<AbstractVcs, Set<FilePath>> entry : myPreparedDeletion.entrySet()) {
      final AbstractVcs vcs = entry.getKey();
      final CheckinEnvironment localChangesProvider = requireNonNull(vcs.getCheckinEnvironment());

      final List<FilePath> filePaths = new ArrayList<>(entry.getValue());
      if (filePaths.isEmpty()) continue;

      localChangesProvider.scheduleMissingFileForDeletion(filePaths);
    }

    for (Map.Entry<AbstractVcs, Set<FilePath>> entry : myPreparedAddition.entrySet()) {
      final AbstractVcs vcs = entry.getKey();
      final CheckinEnvironment localChangesProvider = requireNonNull(vcs.getCheckinEnvironment());

      final List<FilePath> filePaths = new ArrayList<>(entry.getValue());
      if (filePaths.isEmpty()) continue;

      final List<VirtualFile> virtualFiles = new ArrayList<>();
      for (FilePath path : filePaths) {
        VirtualFile vf = path.getVirtualFile();
        if (vf == null) {
          incorrectFilePath.add(path);
        }
        else {
          virtualFiles.add(vf);
        }
      }

      localChangesProvider.scheduleUnversionedFilesForAddition(virtualFiles);
    }

    //if some errors occurred  -> notify
    if (!incorrectFilePath.isEmpty()) {
      notifyAndLogFiles(incorrectFilePath);
    }
  }

  private void notifyAndLogFiles(@NotNull List<FilePath> incorrectFilePath) {
    String message = VcsBundle.message("patch.apply.incorrectly.processed.warning", incorrectFilePath.size(), incorrectFilePath);
    LOG.warn(message);
    VcsNotifier.getInstance(myProject).notifyImportantWarning(PATCH_APPLY_NEW_FILES_ERROR,
                                                              VcsBundle.message("patch.apply.new.files.warning"),
                                                              message);
  }

  private void processDeletion(@NotNull Collection<? extends FilePath> filePaths) {
    Map<VcsRoot, List<FilePath>> map = groupByRoots(myProject, filePaths, identity());

    for (VcsRoot vcsRoot : map.keySet()) {
      AbstractVcs vcs = vcsRoot.getVcs();
      if (vcs == null) continue;

      final CheckinEnvironment localChangesProvider = vcs.getCheckinEnvironment();
      if (localChangesProvider == null) continue;
      final boolean takeDirs = vcs.areDirectoriesVersionedItems();

      final Collection<FilePath> files = map.get(vcsRoot);
      final List<FilePath> toBeDeleted = new ArrayList<>();
      for (FilePath file : files) {
        if (takeDirs || !file.isDirectory()) {
          FilePath parent = file.getParentPath();
          if (parent != null && parent.getIOFile().exists()) {
            toBeDeleted.add(file);
          }
        }
      }

      if (toBeDeleted.isEmpty()) return;
      myAffected.addAll(toBeDeleted);

      if (!vcs.fileListenerIsSynchronous()) {
        myVcsFileListenerContextHelper.ignoreDeleted(toBeDeleted);

        Set<FilePath> paths = myPreparedDeletion.computeIfAbsent(vcs, key -> new HashSet<>());
        paths.addAll(toBeDeleted);
      }
    }
  }

  private void processAddition(@NotNull Collection<? extends FilePath> filePaths) {
    Map<VcsRoot, List<FilePath>> map = groupByRoots(myProject, filePaths, identity());

    for (VcsRoot vcsRoot : map.keySet()) {
      AbstractVcs vcs = vcsRoot.getVcs();
      if (vcs == null) continue;

      final CheckinEnvironment localChangesProvider = vcs.getCheckinEnvironment();
      if (localChangesProvider == null) continue;
      final boolean takeDirs = vcs.areDirectoriesVersionedItems();

      final Collection<FilePath> files = map.get(vcsRoot);
      final List<FilePath> toBeAdded = new ArrayList<>();
      if (takeDirs) {
        final RecursiveCheckAdder adder = new RecursiveCheckAdder(vcsRoot.getPath());
        for (FilePath file : files) {
          adder.process(file);
        }
        toBeAdded.addAll(adder.getToBeAdded());
      }
      else {
        for (FilePath file : files) {
          if (!file.isDirectory()) {
            toBeAdded.add(file);
          }
        }
      }

      if (toBeAdded.isEmpty()) return;
      myAffected.addAll(toBeAdded);

      if (!vcs.fileListenerIsSynchronous()) {
        myVcsFileListenerContextHelper.ignoreAdded(toBeAdded);

        Set<FilePath> paths = myPreparedAddition.computeIfAbsent(vcs, key -> new HashSet<>());
        paths.addAll(toBeAdded);
      }
    }
  }

  private static final class RecursiveCheckAdder {
    private final Set<FilePath> myToBeAdded = new HashSet<>();
    private final VirtualFile myRoot;

    private RecursiveCheckAdder(final VirtualFile root) {
      myRoot = root;
    }

    public void process(final FilePath path) {
      FilePath current = path;
      while (current != null) {
        VirtualFile vf = current.getVirtualFile();
        if (vf == null) {
          vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(current.getPath());
        }
        if (vf == null) {
          return;
        }
        if (!VfsUtilCore.isAncestor(myRoot, vf, true)) return;

        myToBeAdded.add(current);
        current = current.getParentPath();
      }
    }

    public Collection<FilePath> getToBeAdded() {
      return myToBeAdded;
    }
  }
}
