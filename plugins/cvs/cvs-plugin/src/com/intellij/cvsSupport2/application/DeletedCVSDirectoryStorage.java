/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;

import java.io.File;
import java.util.Collection;

public class DeletedCVSDirectoryStorage {
  private final File myRoot;
  public static final String CVS_ADMIN_DIR = CvsUtil.CVS;

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

  public void checkNeedForPurge(File file) {
    if (!file.isDirectory()) return;

    File[] subdirectories = file.listFiles(FileUtilRt.ALL_DIRECTORIES);
    for (File subdirectory : subdirectories) {
      checkNeedForPurge(subdirectory);
    }

    File savedCopy = translatePath(file);
    if (canDeleteSavedCopy(file, savedCopy)) FileUtil.delete(savedCopy);
  }

  public File translatePath(File file) {
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
    return (translatePath(new File(file.getParentFile(), CVS_ADMIN_DIR)).exists());
  }


  private static boolean canDeleteSavedCopy(File original, File copy) {
    File[] savedFiles = copy.listFiles();
    if (savedFiles == null) savedFiles = new File[0];
    for (File savedFile : savedFiles) {
      if (!new File(original, savedFile.getName()).exists()) return false;
    }
    return true;
  }

  public synchronized void deleteIfAdminDirCreated(final VirtualFile file) {
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

  public DeleteHandler createDeleteHandler(Project project, CvsStorageComponent cvsStorageComponent) {
    return new DeleteHandler(project, cvsStorageComponent);
  }
}
