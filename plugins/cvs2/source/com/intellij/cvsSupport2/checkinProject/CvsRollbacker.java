package com.intellij.cvsSupport2.checkinProject;

import com.intellij.cvsSupport2.actions.RestoreFileAction;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.command.CommandProcessor;

import java.io.IOException;

/**
 * author: lesya
 */
public class CvsRollbacker {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.checkinProject.CvsRollbacker");

  private final Project myProject;

  public CvsRollbacker(Project project) {
    myProject = project;
  }

  public boolean rollbackFileModifying(VirtualFile parent, String name) {
    return restoreFile(parent, name);
  }

  public boolean rollbackFileDeleting(VirtualFile parent, String name) {
    return restoreFile(parent, name);
  }

  public boolean rollbackFileCreating(VirtualFile parent, String name) throws IOException {
    return removeFile(parent, name);
  }


  private boolean removeFile(final VirtualFile parent, final String name) throws IOException {
    final IOException[] exception = new IOException[1];
    final boolean[] result = new boolean[1];

    Runnable action = new Runnable() {
      public void run() {
        Runnable writeAction = new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                final VirtualFile file = parent.findChild(name);
                if (file == null) {
                  result[0] = false;
                  return;
                }
                try {
                  file.delete(this);
                }
                catch (IOException e) {
                  exception[0] = e;
                  result[0] = false;
                  return;
                }
                result[0] = true;
              }
            });
          }
        };
        CommandProcessor.getInstance().executeCommand(myProject, writeAction, com.intellij.CvsBundle.message("command.name.rollback.file.creation"), null);
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      action.run();
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(action, ModalityState.defaultModalityState());
    }
    if (exception[0] != null) throw exception[0];
    return result[0];
  }

  private boolean restoreFile(final VirtualFile parent, String name) {
    try {
      new RestoreFileAction(parent, name).actionPerformed(createDataContext());
    }
    catch (Exception e) {
      LOG.error(e);
      return false;
    }
    return true;
  }

  private CvsContext createDataContext() {
    return new CvsContextAdapter() {
      public Project getProject() {
        return myProject;
      }
    };
  }
}
