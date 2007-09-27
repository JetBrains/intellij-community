package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author yole
 */
public class CvsFileOperationsHandler implements LocalFileOperationsHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.application.CvsFileOperationsHandler");

  private final Project myProject;
  private final CvsStorageSupportingDeletionComponent myComponent;
  private boolean myInternalDelete = false;

  public CvsFileOperationsHandler(final Project project, final CvsStorageSupportingDeletionComponent component) {
    myProject = project;
    myComponent = component;
  }

  public boolean delete(final VirtualFile file) throws IOException {
    return processDeletedFile(file);
  }

  private boolean processDeletedFile(final VirtualFile file) throws IOException {
    if (myInternalDelete) return false;
    file.putUserData(CvsStorageSupportingDeletionComponent.FILE_VCS,
                     ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file));
    if (!CvsUtil.fileIsUnderCvs(file)) return false;
    myComponent.getDeleteHandler().addDeletedRoot(file);
    if (file.isDirectory()) {
      myInternalDelete = true;
      try {
        deleteFilesInVFS(file);
      }
      finally {
        myInternalDelete = false;
      }
      return true;
    }
    return false;
  }

  private void deleteFilesInVFS(final VirtualFile file) throws IOException {
    for(VirtualFile child: file.getChildren()) {
      if (child.isDirectory()) {
        if (DeletedCVSDirectoryStorage.isAdminDir(child)) continue;
        deleteFilesInVFS(child);
      }
      else {
        child.delete(this);
      }
    }
  }

  public boolean move(final VirtualFile file, final VirtualFile toDir) throws IOException {
    return doMoveRename(file, toDir, file.getName());
  }

  @Nullable
  public File copy(final VirtualFile file, final VirtualFile toDir, final String copyName) throws IOException {
    return null;
  }

  public boolean rename(final VirtualFile file, final String newName) throws IOException {
    return doMoveRename(file, file.getParent(), newName);
  }

  private boolean doMoveRename(final VirtualFile file, final VirtualFile newParent, final String newName) throws IOException {
    if (!CvsUtil.fileIsUnderCvs(file)) return false;
    if (!file.isDirectory()) return false;
    if (newParent == null) return false;
    myComponent.getDeleteHandler().addDeletedRoot(file);
    File newFile = new File(newParent.getPath(), newName);
    newFile.mkdir();
    copyDirectoryStructure(file, newFile);
    myComponent.getAddHandler().addFile(newFile);
    return true;
  }

  private static void copyDirectoryStructure(final VirtualFile file, final File newFile) throws IOException {
    for(VirtualFile child: file.getChildren()) {
      File newChild = new File(newFile, child.getName());
      if (child.isDirectory()) {
        if (DeletedCVSDirectoryStorage.isAdminDir(child)) continue;
        newChild.mkdir();
        copyDirectoryStructure(child, newChild);
      }
      else {
        new File(child.getPath()).renameTo(newChild);
      }
    }
  }

  public boolean createFile(final VirtualFile dir, final String name) throws IOException {
    return false;
  }

  public boolean createDirectory(final VirtualFile dir, final String name) throws IOException {
    return false;
  }
}
