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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class RefreshVFsSynchronously {
  private RefreshVFsSynchronously() {
  }

  public static void updateAllChanged(final UpdatedFiles updatedFiles) {
    // approx so ok
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.setIndeterminate(false);
    }
    final int num = getFilesNum(updatedFiles);

    wrapIntoLock(new Runnable() {
      public void run() {
        UpdateFilesHelper.iterateFileGroupFilesDeletedOnServerFirst(updatedFiles, new MyRefreshCallback(num, progressIndicator));
      }
    });
  }

  private static int getFilesNum(final UpdatedFiles files) {
    int result = 0;
    for (FileGroup group : files.getTopLevelGroups()) {
      result += group.getImmediateFilesSize();
      final List<FileGroup> children = group.getChildren();
      for (FileGroup child : children) {
        result += child.getImmediateFilesSize();
      }
    }
    return result;
  }

  @Nullable
  public static VirtualFile findCreatedFile(final File root) {
    refresh(root);
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    return lfs.findFileByIoFile(root);
  }

  private static void refresh(final File root) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile vFile = lfs.refreshAndFindFileByIoFile(root);
    if (vFile != null) {
      vFile.refresh(false, false);
      return;
    }
  }

  private static void refreshDeletedOrReplaced(final File root) {
    final File parent = root.getParentFile();
    VirtualFile vf = null;
    // parent should also notice the change
    final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    final VirtualFile rootVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);
    if (parent != null) {
      vf = localFileSystem.refreshAndFindFileByIoFile(parent);
    }
    if (vf == null) {
      vf = rootVf;
    }
    if (vf != null) {
      ((NewVirtualFile)vf).markDirtyRecursively();
      vf.refresh(false, true);
    }
  }

  public static void updateChangesForRollback(final List<Change> changes) {
    updateChangesImpl(changes, RollbackChangeWrapper.ourInstance);
  }

  public static void updateChanges(final List<Change> changes) {
    updateChangesImpl(changes, DirectChangeWrapper.ourInstance);
  }

  private static void updateChangesImpl(final List<Change> changes, final ChangeWrapper wrapper) {
    // approx so ok
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
    if (pi != null) {
      pi.setIndeterminate(false);
    }
    final double num = changes.size();

    int cnt = 0;
    final FilesForRefresh filesForRefresh = new FilesForRefresh();
    for (Change change : changes) {
      if ((! wrapper.beforeNull(change)) && (wrapper.movedOrRenamedOrReplaced(change) || (wrapper.afterNull(change)))) {
        refreshDeletedOrReplaced(wrapper.getBeforeFile(change));
      } else if (! wrapper.beforeNull(change)) {
        refresh(wrapper.getBeforeFile(change));
      }
      if ((! wrapper.afterNull(change)) && (! Comparing.equal(change.getAfterRevision(), change.getBeforeRevision()))) {
        refreshDeletedOrReplaced(wrapper.getAfterFile(change));
      }
      if (pi != null) {
        ++ cnt;
        pi.setFraction(cnt/num);
        pi.setText2("Refreshing: " + change.toString());
      }
    }
  }

  private static class RollbackChangeWrapper implements ChangeWrapper {
    private static final RollbackChangeWrapper ourInstance = new RollbackChangeWrapper();

    public boolean beforeNull(Change change) {
      return change.getAfterRevision() == null;
    }

    public boolean afterNull(Change change) {
      return change.getBeforeRevision() == null;
    }

    public File getBeforeFile(Change change) {
      return beforeNull(change) ? null : change.getAfterRevision().getFile().getIOFile();
    }

    public File getAfterFile(Change change) {
      return afterNull(change) ? null : change.getBeforeRevision().getFile().getIOFile();
    }

    public boolean movedOrRenamedOrReplaced(Change change) {
      return change.isIsReplaced() || change.isRenamed() || change.isIsReplaced();
    }
  }

  private static class DirectChangeWrapper implements ChangeWrapper {
    private static final DirectChangeWrapper ourInstance = new DirectChangeWrapper();

    public boolean beforeNull(Change change) {
      return change.getBeforeRevision() == null;
    }

    public boolean afterNull(Change change) {
      return change.getAfterRevision() == null;
    }

    @Nullable
    public File getBeforeFile(Change change) {
      return beforeNull(change) ? null : change.getBeforeRevision().getFile().getIOFile();
    }

    @Nullable
    public File getAfterFile(Change change) {
      return afterNull(change) ? null : change.getAfterRevision().getFile().getIOFile();
    }

    public boolean movedOrRenamedOrReplaced(Change change) {
      return change.isIsReplaced() || change.isRenamed() || change.isIsReplaced();
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

  private static void wrapIntoLock(final Runnable runnable) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.startNonCancelableSection();
      indicator.setText(VcsBundle.message("progress.text.synchronizing.files"));
      indicator.setText2("");
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        try {
          // common lock for all refreshes inside
          runnable.run();
        }
        finally {
          semaphore.up();
        }
      }
    });
    semaphore.waitFor();
  }

  private static class MyRefreshCallback implements UpdateFilesHelper.Callback {
    private int myCnt;
    private final double myTotal;
    private final ProgressIndicator myProgressIndicator;

    private MyRefreshCallback(final int total, final ProgressIndicator progressIndicator) {
      myTotal = total;
      myProgressIndicator = progressIndicator;
      myCnt = 0;
    }

    public void onFile(String filePath, String groupId) {
      final File file = new File(filePath);
      if (FileGroup.REMOVED_FROM_REPOSITORY_ID.equals(groupId)) {
        refreshDeletedOrReplaced(file);
      } else {
        refresh(file);
      }
      if (myProgressIndicator != null) {
        ++ myCnt;
        myProgressIndicator.setFraction(myCnt/myTotal);
        myProgressIndicator.setText2("Refreshing " + filePath);
      }
    }
  }
}
