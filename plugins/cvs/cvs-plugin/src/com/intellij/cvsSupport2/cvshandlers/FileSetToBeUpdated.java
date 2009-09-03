package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.CvsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;


/**
 * author: lesya
 */
public abstract class FileSetToBeUpdated {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvshandlers.FileSetToBeUpdated");

  public static FileSetToBeUpdated allFiles() {
    return new AllFilesInProject();
  }

  public static FileSetToBeUpdated selectedFiles(FilePath[] files) {
    return new SelectedFiles(files);
  }

  public static FileSetToBeUpdated selectedFiles(VirtualFile[] files) {
    return new SelectedFiles(files);
  }

  public final static FileSetToBeUpdated EMPTY = new FileSetToBeUpdated() {
    public void refreshFilesAsync(Runnable postRunnable) {
      if (postRunnable != null) {
        postRunnable.run();
      }
    }

    public void refreshFilesSync() {
    }

    protected void setSynchronizingFilesTextToProgress(ProgressIndicator progressIndicator) {
      
    }
  };

  public abstract void refreshFilesAsync(Runnable postRunnable);
  public abstract void refreshFilesSync();

  protected void setSynchronizingFilesTextToProgress(ProgressIndicator progressIndicator) {
    progressIndicator.setText(CvsBundle.message("progress.text.synchronizing.files"));
    progressIndicator.setText2("");
  }
}
