package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.openapi.vfs.VirtualFileManager;

/**
 * author: lesya
 */
class AllFilesInProject extends FileSetToBeUpdated{

  public AllFilesInProject() {
  }

  public void refreshFilesAsync(Runnable postRunnable) {
    VirtualFileManager.getInstance().refresh(true, postRunnable);
  }

  public void refreshFilesSync() {
    VirtualFileManager.getInstance().refresh(false);
  }
}
