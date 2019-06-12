// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;

public class DeletedCVSDirectoryStorage {
  private final File myRoot;
  private static final String CVS_ADMIN_DIR = CvsUtil.CVS;

  private final Collection<VirtualFile> myFilesToDelete = new HashSet<>();

  public DeletedCVSDirectoryStorage(File root) {
    myRoot = root;
  }

  public static boolean isAdminDir(File subdirectory) {
    return subdirectory.getName().equals(CVS_ADMIN_DIR);
  }

  public static boolean isAdminDir(VirtualFile file) {
    if (!file.isDirectory()) return false;
    return file.getName().equals(CVS_ADMIN_DIR);
  }

  void checkNeedForPurge(File file) {
    if (!file.isDirectory()) return;

    File[] subdirectories = file.listFiles(FileUtilRt.ALL_DIRECTORIES);
    for (File subdirectory : subdirectories) {
      checkNeedForPurge(subdirectory);
    }

    File savedCopy = translatePath(file);
    if (canDeleteSavedCopy(file, savedCopy)) FileUtil.delete(savedCopy);
  }

  private File translatePath(File file) {
    return translatePath(file.getAbsolutePath());
  }

  private File translatePath(String path) {
    return new File(myRoot, path.replace(':', '_'));
  }

  public File alternatePath(File file) {
    return gotControlOver(file) ? translatePath(file) : file;
  }

  private boolean gotControlOver(File file) {
    return !file.exists() && (contains(file) || containsCvsDirFor(file));
  }

  public boolean contains(File file) {
    return translatePath(file).exists();
  }

  private boolean containsCvsDirFor(File file) {
    return translatePath(new File(file.getParentFile(), CVS_ADMIN_DIR)).exists();
  }


  private static boolean canDeleteSavedCopy(File original, File copy) {
    File[] savedFiles = copy.listFiles();
    if (savedFiles == null) savedFiles = new File[0];
    for (File savedFile : savedFiles) {
      if (!new File(original, savedFile.getName()).exists()) return false;
    }
    return true;
  }

  synchronized void deleteIfAdminDirCreated(@NotNull VirtualFile file) {
    if (isAdminDir(file)) {
      myFilesToDelete.add(file);
    }
    else if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (VirtualFile child : children) {
        deleteIfAdminDirCreated(child);
      }
    }
  }

  @NotNull
  DeleteHandler createDeleteHandler(Project project, CvsStorageSupportingDeletionComponent cvsStorageComponent) {
    return new DeleteHandler(project, cvsStorageComponent);
  }
}
