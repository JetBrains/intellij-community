package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.HashSet;
import java.util.Set;

public class FoldersCutDownWorker {
  private final Set<String> myPaths;

  public FoldersCutDownWorker() {
    myPaths = new HashSet<String>();
  }

  public boolean addCurrent(final VirtualFile file) {
    for (String path : myPaths) {
      if (FileUtil.startsWith(file.getPath(), path)) {
        return false;
      }
    }

    myPaths.add(file.getPath());
    return true;
  }
}
