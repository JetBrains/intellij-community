package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.CvsBundle;


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

  public void refreshSync() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          refreshFilesSync();
        }
      });
    }
    else {
      ModalityState modalityState = ModalityState.NON_MODAL;
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      if (progressIndicator != null) {
        setSynchronizingFilesTextToProgress(progressIndicator);
        modalityState = progressIndicator.getModalityState();
      }

      final Semaphore semaphore = new Semaphore();
      semaphore.down();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          LOG.info("refreshFilesAsync, modalityState=" + ModalityState.current());

          refreshFilesAsync(new Runnable() {
            public void run() {
              semaphore.up();
            }
          });
        }
      }, modalityState);
      semaphore.waitFor();
    }
  }

  protected void setSynchronizingFilesTextToProgress(ProgressIndicator progressIndicator) {
    progressIndicator.setText(CvsBundle.message("progress.text.synchronizing.files"));
    progressIndicator.setText2("");
  }
}
