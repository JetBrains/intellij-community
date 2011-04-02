
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class ApplyPatchContext {
  private final VirtualFile myBaseDir;
  private final int mySkipTopDirs;
  private final boolean myCreateDirectories;
  private final boolean myAllowRename;
  private Map<VirtualFile, String> myPendingRenames = null;
  private final Map<VirtualFile, FilePath> myPathsBeforeRename = new HashMap<VirtualFile, FilePath>();
  private final TreeSet<String> myMissingDirectories = new TreeSet<String>();
  private final List<FilePath> myAffectedFiles = new ArrayList<FilePath>();
  
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

  public void addPendingRename(VirtualFile file, String newName) {
    if (myPendingRenames == null) {
      myPendingRenames = new HashMap<VirtualFile, String>();
    }
    myPendingRenames.put(file, newName);
  }

  public void applyPendingRenames() throws IOException {
    if (myPendingRenames != null) {
      for(Map.Entry<VirtualFile, String> entry: myPendingRenames.entrySet()) {
        final VirtualFile file = entry.getKey();
        registerBeforeRename(file);
        file.rename(FilePatch.class, entry.getValue());
        addAffectedFile(file);
      }
      myPendingRenames = null;
    }
  }

  public void registerMissingDirectory(final VirtualFile existingDir, final String[] pathNameComponents, final int firstMissingIndex) {
    String path = existingDir.getPath();
    for(int i=firstMissingIndex; i<pathNameComponents.length-1; i++) {
      path += "/" + pathNameComponents [i];
      myMissingDirectories.add(FileUtil.toSystemDependentName(path));
    }
  }

  public Collection<String> getMissingDirectories() {
    return Collections.unmodifiableSet(myMissingDirectories);
  }

  public void addAffectedFile(FilePath filePath) {
    myAffectedFiles.add(filePath);
  }

  public List<FilePath> getAffectedFiles() {
    return Collections.unmodifiableList(myAffectedFiles);
  }

  public void registerBeforeRename(final VirtualFile file) {
    final FilePathImpl path = new FilePathImpl(new File(file.getPath()), file.isDirectory());
    addAffectedFile(path);
    myPathsBeforeRename.put(file, path);
  }

  public FilePath getPathBeforeRename(final VirtualFile file) {
    final FilePath path = myPathsBeforeRename.get(file);
    if (path != null) return path;
    return new FilePathImpl(file);
  }

  public void addAffectedFile(final VirtualFile file) {
    addAffectedFile(new FilePathImpl(new File(file.getPath()), file.isDirectory()));
  }
}
