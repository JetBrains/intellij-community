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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

/**
 * @author max
 */
public class VirtualFileHolder implements FileHolder {
  private final Set<VirtualFile> myFiles = new HashSet<VirtualFile>();
  private final Project myProject;
  private final HolderType myType;
  private int myNumDirs;

  public VirtualFileHolder(Project project, final HolderType type) {
    myProject = project;
    myType = type;
  }

  public HolderType getType() {
    return myType;
  }

  public void cleanAll() {
    myFiles.clear();
    myNumDirs = 0;
  }

  // returns number of removed directories
  static int cleanScope(final Project project, final Collection<VirtualFile> files, final VcsDirtyScope scope) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
      public Integer compute() {
        int result = 0;
        // to avoid deadlocks caused by incorrect lock ordering, need to lock on this after taking read action
        if (project.isDisposed() || files.isEmpty()) return 0;
        final List<VirtualFile> currentFiles = new ArrayList<VirtualFile>(files);
        if (scope.getRecursivelyDirtyDirectories().size() == 0) {
          final Set<FilePath> dirtyFiles = scope.getDirtyFiles();
          boolean cleanedDroppedFiles = false;
          for(FilePath dirtyFile: dirtyFiles) {
            VirtualFile f = dirtyFile.getVirtualFile();
            if (f != null) {
              files.remove(f);
              if (f.isDirectory()) ++ result;
            }
            else {
              if (!cleanedDroppedFiles) {
                cleanedDroppedFiles = true;
                for(VirtualFile file: currentFiles) {
                  if (fileDropped(project, file)) {
                    files.remove(file);
                    if (file.isDirectory()) ++ result;
                  }
                }
              }
            }
          }
        }
        else {
          for (VirtualFile file : currentFiles) {
            if (fileDropped(project, file) || scope.belongsTo(new FilePathImpl(file))) {
              files.remove(file);
              if (file.isDirectory()) ++ result;
            }
          }
        }
        return result;
      }
    });
  }

  public void cleanScope(final VcsDirtyScope scope) {
    myNumDirs -= cleanScope(myProject, myFiles, scope);
  }

  private static boolean fileDropped(final Project project, final VirtualFile file) {
    return !file.isValid() || ProjectLevelVcsManager.getInstance(project).getVcsFor(file) == null;
  }

  public void addFile(VirtualFile file) {
    myFiles.add(file);
    if (file.isDirectory()) ++ myNumDirs;
  }

  public void removeFile(VirtualFile file) {
    myFiles.remove(file);
    if (file.isDirectory()) -- myNumDirs;
  }

  public List<VirtualFile> getFiles() {
    return new ArrayList<VirtualFile>(myFiles);
  }

  public VirtualFileHolder copy() {
    final VirtualFileHolder copyHolder = new VirtualFileHolder(myProject, myType);
    copyHolder.myFiles.addAll(myFiles);
    copyHolder.myNumDirs = myNumDirs;
    return copyHolder;
  }

  public boolean containsFile(final VirtualFile file) {
    return myFiles.contains(file);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VirtualFileHolder that = (VirtualFileHolder)o;

    if (!myFiles.equals(that.myFiles)) return false;

    return true;
  }

  public int hashCode() {
    return myFiles.hashCode();
  }

  public int getSize() {
    return myFiles.size();
  }

  public int getNumDirs() {
    return myNumDirs;
  }
}
