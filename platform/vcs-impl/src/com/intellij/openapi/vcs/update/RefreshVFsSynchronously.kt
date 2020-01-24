// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class RefreshVFsSynchronously {
  private static final Logger LOG = Logger.getInstance(RefreshVFsSynchronously.class);

  private RefreshVFsSynchronously() {
  }

  public static void updateAllChanged(@NotNull final UpdatedFiles updatedFiles) {
    FilesToRefreshCollector callback = new FilesToRefreshCollector();
    UpdateFilesHelper.iterateFileGroupFilesDeletedOnServerFirst(updatedFiles, callback);

    refreshDeletedOrReplaced(callback.getToRefreshDeletedOrReplaced());
    refreshFiles(callback.getToRefresh());
  }

  public static void refreshFiles(@NotNull Collection<? extends File> files) {
    Collection<VirtualFile> filesToRefresh = new HashSet<>();
    for (File file : files) {
      VirtualFile vf = findFirstValidVirtualParent(file);
      if (vf != null) {
        filesToRefresh.add(vf);
      }
    }
    VfsUtil.markDirtyAndRefresh(false, false, false, filesToRefresh.toArray(VirtualFile.EMPTY_ARRAY));
  }

  private static void refreshDeletedOrReplaced(@NotNull Collection<? extends File> deletedOrReplaced) {
    Collection<VirtualFile> filesToRefresh = new HashSet<>();
    for (File file : deletedOrReplaced) {
      File parent = file.getParentFile();
      VirtualFile vf = findFirstValidVirtualParent(parent);
      if (vf != null) {
        filesToRefresh.add(vf);
      }
    }
    VfsUtil.markDirtyAndRefresh(false, true, false, filesToRefresh.toArray(VirtualFile.EMPTY_ARRAY));
  }

  @Nullable
  private static VirtualFile findFirstValidVirtualParent(@Nullable File file) {
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile vf = null;
    while (file != null && (vf == null || !vf.isValid())) {
      vf = lfs.findFileByIoFile(file);
      file = file.getParentFile();
    }
    return vf == null || !vf.isValid() ? null : vf;
  }

  public static void updateChangesForRollback(final List<? extends Change> changes) {
    updateChangesImpl(changes, RollbackChangeWrapper.ourInstance);
  }

  public static void updateChanges(final Collection<? extends Change> changes) {
    updateChangesImpl(changes, DirectChangeWrapper.ourInstance);
  }

  private static void updateChangesImpl(final Collection<? extends Change> changes, final ChangeWrapper wrapper) {
    Collection<File> deletedOrReplaced = new HashSet<>();
    Collection<File> toRefresh = new HashSet<>();
    for (Change change : changes) {
      if ((! wrapper.beforeNull(change)) && (wrapper.movedOrRenamedOrReplaced(change) || (wrapper.afterNull(change)))) {
        deletedOrReplaced.add(wrapper.getBeforeFile(change));
      } else if (!wrapper.beforeNull(change)) {
        toRefresh.add(wrapper.getBeforeFile(change));
      }
      if ((! wrapper.afterNull(change)) &&
          (wrapper.beforeNull(change) || (! Comparing.equal(change.getAfterRevision().getFile(), change.getBeforeRevision().getFile())))
         ) {
        toRefresh.add(wrapper.getAfterFile(change));
      }
    }

    refreshFiles(toRefresh);
    refreshDeletedOrReplaced(deletedOrReplaced);
  }

  private static class RollbackChangeWrapper implements ChangeWrapper {
    private static final RollbackChangeWrapper ourInstance = new RollbackChangeWrapper();

    @Override
    public boolean beforeNull(Change change) {
      return change.getAfterRevision() == null;
    }

    @Override
    public boolean afterNull(Change change) {
      return change.getBeforeRevision() == null;
    }

    @Override
    public File getBeforeFile(Change change) {
      return beforeNull(change) ? null : change.getAfterRevision().getFile().getIOFile();
    }

    @Override
    public File getAfterFile(Change change) {
      return afterNull(change) ? null : change.getBeforeRevision().getFile().getIOFile();
    }

    @Override
    public boolean movedOrRenamedOrReplaced(Change change) {
      return change.isMoved() || change.isRenamed() || change.isIsReplaced();
    }
  }

  private static class DirectChangeWrapper implements ChangeWrapper {
    private static final DirectChangeWrapper ourInstance = new DirectChangeWrapper();

    @Override
    public boolean beforeNull(Change change) {
      return change.getBeforeRevision() == null;
    }

    @Override
    public boolean afterNull(Change change) {
      return change.getAfterRevision() == null;
    }

    @Override
    @Nullable
    public File getBeforeFile(Change change) {
      return beforeNull(change) ? null : change.getBeforeRevision().getFile().getIOFile();
    }

    @Override
    @Nullable
    public File getAfterFile(Change change) {
      return afterNull(change) ? null : change.getAfterRevision().getFile().getIOFile();
    }

    @Override
    public boolean movedOrRenamedOrReplaced(Change change) {
      return change.isMoved() || change.isRenamed() || change.isIsReplaced();
    }
  }

  private interface ChangeWrapper {
    boolean beforeNull(final Change change);
    boolean afterNull(final Change change);
    @Nullable
    File getBeforeFile(final Change change);
    @Nullable
    File getAfterFile(final Change change);
    boolean movedOrRenamedOrReplaced(final Change change);
  }

  private static class FilesToRefreshCollector implements UpdateFilesHelper.Callback {
    private final Collection<File> myToRefresh = new THashSet<>();
    private final Collection<File> myToRefreshDeletedOrReplaced = new THashSet<>();

    @Override
    public void onFile(String filePath, String groupId) {
      final File file = new File(filePath);
      if (FileGroup.REMOVED_FROM_REPOSITORY_ID.equals(groupId) || FileGroup.MERGED_WITH_TREE_CONFLICT.endsWith(groupId)) {
        myToRefreshDeletedOrReplaced.add(file);
      }
      else {
        myToRefresh.add(file);
      }
    }

    @NotNull
    public Collection<File> getToRefresh() {
      return myToRefresh;
    }

    @NotNull
    public Collection<File> getToRefreshDeletedOrReplaced() {
      return myToRefreshDeletedOrReplaced;
    }
  }

}
