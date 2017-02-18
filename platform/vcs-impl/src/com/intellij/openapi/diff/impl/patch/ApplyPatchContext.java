
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class ApplyPatchContext {
  private final VirtualFile myBaseDir;
  private final int mySkipTopDirs;
  private final boolean myCreateDirectories;
  private final boolean myAllowRename;
  private final Map<VirtualFile, FilePath> myPathsBeforeRename = new HashMap<>();

  public ApplyPatchContext(final VirtualFile baseDir, final int skipTopDirs, final boolean createDirectories, final boolean allowRename) {
    myBaseDir = baseDir;
    mySkipTopDirs = skipTopDirs;
    myCreateDirectories = createDirectories;
    myAllowRename = allowRename;
  }

  public VirtualFile getBaseDir() {
    return myBaseDir;
  }

  public int getSkipTopDirs() {
    return mySkipTopDirs;
  }

  public boolean isAllowRename() {
    return myAllowRename;
  }

  public boolean isCreateDirectories() {
    return myCreateDirectories;
  }

  public ApplyPatchContext getPrepareContext() {
    return new ApplyPatchContext(myBaseDir, mySkipTopDirs, false, false);
  }

  public void registerBeforeRename(final VirtualFile file) {
    FilePath path = VcsUtil.getFilePath(file);
    myPathsBeforeRename.put(file, path);
  }

  public FilePath getPathBeforeRename(final VirtualFile file) {
    final FilePath path = myPathsBeforeRename.get(file);
    if (path != null) return path;
    return VcsUtil.getFilePath(file);
  }
}
