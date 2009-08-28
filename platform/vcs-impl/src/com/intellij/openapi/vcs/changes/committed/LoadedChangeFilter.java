package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class LoadedChangeFilter {
  private final List<File> myRoots;

  public LoadedChangeFilter(List<File> roots) {
    myRoots = roots;
  }

  public boolean ok(final CommittedChangeList list) {
    final Collection<Change> changes = list.getChanges();
    for (Change change : changes) {
      if (change.getBeforeRevision() != null) {
        final FilePath path = change.getBeforeRevision().getFile();
        if (ok(path.getIOFile())) {
          return true;
        }
      }
      if (change.getAfterRevision() != null) {
        if (! (change.getBeforeRevision() != null &&
               change.getAfterRevision().getFile().equals(change.getBeforeRevision().getFile()))) {
          final FilePath path = change.getAfterRevision().getFile();
          if (ok(path.getIOFile())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean ok(final File vf) {
    for (File root : myRoots) {
      if (VfsUtil.isAncestor(root, vf, false)) {
        return true;
      }
    }
    return false;
  }
}
