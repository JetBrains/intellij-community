package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

/**
 * author: lesya
 */
public abstract class AnyProcessedFiles extends FileSetToBeUpdated {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvshandlers.AnyProcessedFiles");

  public abstract Collection<VirtualFile> getFiles();


  public void refreshFilesAsync(final Runnable postRunnable) {
    final VirtualFile[] files = getFiles().toArray(VirtualFile.EMPTY_ARRAY);
    final int[] index = new int[]{0};
    LOG.info("files.length=" + files.length);
    Runnable runnable = new Runnable() {
      public void run() {
        if (index[0] < files.length){
          VirtualFile file = files[index[0]++];
          if (file.isValid()){
            LOG.info("Refreshing:" + file);
            file.refresh(true, true, this);
          }
          else{
            LOG.info("Skipping file");
            this.run();
          }
        }
        else{
          LOG.info("postRunnable!");
          if (postRunnable != null){
            postRunnable.run();
          }
        }
      }
    };
    runnable.run();

    /*
    if (project != null){
      FileStatusManager fileStatusManager = FileStatusManager.getInstance(project);
      for (Iterator each = getFiles().iterator(); each.hasNext();) {
        fileStatusManager.fileStatusChanged((VirtualFile)each.next());
      }
    }
    */
  }

  public void refreshFilesSync() {
    for(VirtualFile file: getFiles()) {
      file.refresh(false, true);
    }
  }
}
