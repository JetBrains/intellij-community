package com.intellij.cvsSupport2.application;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import org.jetbrains.annotations.NonNls;

public abstract class CvsStorageComponent extends VirtualFileAdapter implements ProjectComponent{
  private boolean myIsActive = false;
  public static final CvsStorageComponent ABSENT_STORAGE = new CvsStorageComponent() {

    public void purge() {
    }

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

    public void sync() {
    }

    public String getComponentName() {
      return "CvsStorageComponent.Absent";
    }
  };

  public abstract void purge();
  public abstract void init(Project project, boolean synch);
  public abstract void dispose();

  public void projectOpened() {
    myIsActive = true;
  }

  public void projectClosed() {
    myIsActive = false;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public String getComponentName() {
    return "CvsStorageComponent";
  }

  public boolean getIsActive() {
    return myIsActive;
  }

  public abstract void deleteIfAdminDirCreated(VirtualFile addedFile);

  public abstract void sync();
}
