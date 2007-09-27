package com.intellij.cvsSupport2.application;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;

public abstract class CvsStorageComponent extends VirtualFileAdapter {
  protected boolean myIsActive = false;
  public static final CvsStorageComponent ABSENT_STORAGE = new CvsStorageComponent() {

    public void init(Project project, boolean synch) {
    }

    public void dispose() {
    }

    public void projectOpened() {
    }

    public void projectClosed() {
    }

    public void disposeComponent() {
    }

    public void initComponent() { }

    public void deleteIfAdminDirCreated(VirtualFile addedFile) {
    }

    public String getComponentName() {
      return "CvsStorageComponent.Absent";
    }
  };

  public abstract void init(Project project, boolean synch);
  public abstract void dispose();

  public boolean getIsActive() {
    return myIsActive;
  }

  public abstract void deleteIfAdminDirCreated(VirtualFile addedFile);

}
