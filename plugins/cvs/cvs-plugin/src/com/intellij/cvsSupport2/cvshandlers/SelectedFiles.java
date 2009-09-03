package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;

/**
 * author: lesya
 */

class SelectedFiles extends AnyProcessedFiles {
  private final Collection<VirtualFile> myFiles = new ArrayList<VirtualFile>();

  public SelectedFiles(FilePath[] files) {
    for (int i = 0; i < files.length; i++) {
      FilePath file = files[i];
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        myFiles.add(virtualFile);
      }
      else {
        myFiles.add(file.getVirtualFileParent());
      }
    }
  }

  public SelectedFiles(VirtualFile[] files) {
    myFiles.addAll(Arrays.asList(files));
  }


  public Collection<VirtualFile> getFiles() {
    return myFiles;
  }
}
