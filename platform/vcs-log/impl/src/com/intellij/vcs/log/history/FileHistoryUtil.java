// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vcs.vfs.VcsVirtualFolder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.ObjectUtils.notNull;

public class FileHistoryUtil {
  @Nullable
  public static VirtualFile createVcsVirtualFile(@Nullable VcsFileRevision revision) {
    if (!VcsHistoryUtil.isEmpty(revision)) {
      if (revision instanceof VcsFileRevisionEx) {
        FilePath path = ((VcsFileRevisionEx)revision).getPath();
        return path.isDirectory()
               ? new VcsVirtualFolder(path.getPath(), null, VcsFileSystem.getInstance())
               : new VcsVirtualFile(path.getPath(), revision, VcsFileSystem.getInstance());
      }
    }
    return null;
  }

  @NotNull
  static List<Change> collectRelevantChanges(@NotNull VcsFullCommitDetails details,
                                             @NotNull Condition<Change> isRelevant) {
    List<Change> changes = ContainerUtil.filter(details.getChanges(), isRelevant);
    if (!changes.isEmpty()) return changes;
    if (details.getParents().size() > 1) {
      for (int parent = 0; parent < details.getParents().size(); parent++) {
        List<Change> changesToParent = ContainerUtil.filter(details.getChanges(parent), isRelevant);
        if (!changesToParent.isEmpty()) return changesToParent;
      }
    }
    return Collections.emptyList();
  }


  static boolean affectsFiles(@NotNull Change change, @NotNull Set<FilePath> files) {
    ContentRevision revision = notNull(chooseNotNull(change.getAfterRevision(), change.getBeforeRevision()));
    return files.contains(revision.getFile());
  }

  static boolean affectsDirectories(@NotNull Change change, @NotNull Set<FilePath> directories) {
    FilePath file = notNull(chooseNotNull(change.getAfterRevision(), change.getBeforeRevision())).getFile();
    return ContainerUtil.find(directories, dir -> VfsUtilCore.isAncestor(dir.getIOFile(), file.getIOFile(), false)) != null;
  }
}
