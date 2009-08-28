package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.FilePathImpl;

public class FilePathUnderVcs {
  private final FilePath myPath;
  private final AbstractVcs myVcs;

  public FilePathUnderVcs(final FilePath path, final AbstractVcs vcs) {
    myPath = path;
    myVcs = vcs;
  }

  FilePathUnderVcs(final VcsRoot root) {
    myPath = new FilePathImpl(root.path);
    myVcs = root.vcs;
  }

  public FilePath getPath() {
    return myPath;
  }

  public AbstractVcs getVcs() {
    return myVcs;
  }
}
