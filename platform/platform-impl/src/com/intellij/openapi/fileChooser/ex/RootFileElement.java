package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class RootFileElement extends FileElement {
  private final VirtualFile[] myFiles;
  private final boolean myShowFileSystemRoots;
  private Object[] myChildren;

  public RootFileElement(VirtualFile[] files, String name, boolean showFileSystemRoots) {
    super(files.length == 1 ? files[0] : null, name);
    myFiles = files;
    myShowFileSystemRoots = showFileSystemRoots;
  }

  public Object[] getChildren() {
    if (myFiles.length <= 1 && myShowFileSystemRoots) {
      return getFileSystemRoots();
    }
    if (myChildren == null) {
      myChildren = createFileElementArray();
    }
    return myChildren;
  }

  private Object[] createFileElementArray() {
    final List<FileElement> roots = new ArrayList<FileElement>();
    for (final VirtualFile file : myFiles) {
      if (file != null) {
        roots.add(new FileElement(file, file.getPresentableUrl()));
      }
    }
    return ArrayUtil.toObjectArray(roots);
  }

  private static Object[] getFileSystemRoots() {
    File[] roots = File.listRoots();
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    HashSet<FileElement> rootChildren = new HashSet<FileElement>();
    for (File root : roots) {
      String path = root.getAbsolutePath();
      path = path.replace(File.separatorChar, '/');
      VirtualFile file = localFileSystem.findFileByPath(path);
      if (file == null) continue;
      rootChildren.add(new FileElement(file, file.getPresentableUrl()));
    }
    return ArrayUtil.toObjectArray(rootChildren);
  }
}
