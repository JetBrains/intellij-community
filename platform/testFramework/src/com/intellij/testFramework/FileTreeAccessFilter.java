package com.intellij.testFramework;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import gnu.trove.THashSet;

import java.util.Set;

/**
* Created by IntelliJ IDEA.
* User: cdr
* Date: Oct 2, 2009
* Time: 2:41:53 PM
* To change this template use File | Settings | File Templates.
*/
public class FileTreeAccessFilter implements VirtualFileFilter {
  protected final Set<VirtualFile> myAddedClasses = new THashSet<VirtualFile>();

  public boolean accept(VirtualFile file) {
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();

    if (myAddedClasses.contains(file)) return false;

    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
    return (fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS) && !file.getName().equals("package-info.java");
  }

  public void allowTreeAccessForFile(VirtualFile file) {
    myAddedClasses.add(file);
  }
}
