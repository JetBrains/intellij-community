// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author max
 */
public class VirtualFileHolder implements FileHolder {
  private final Set<VirtualFile> myFiles = new HashSet<>();
  private final Project myProject;
  private final HolderType myType;

  public VirtualFileHolder(Project project, final HolderType type) {
    myProject = project;
    myType = type;
  }

  public HolderType getType() {
    return myType;
  }

  @Override
  public void notifyVcsStarted(AbstractVcs vcs) {
  }

  public void cleanAll() {
    myFiles.clear();
  }

  static void cleanScope(final Project project, final Collection<VirtualFile> files, final VcsModifiableDirtyScope scope) {
    ReadAction.run(() -> {
      if (project.isDisposed() || files.isEmpty()) return;

      if (scope.getRecursivelyDirtyDirectories().size() == 0) {
        final Set<FilePath> dirtyFiles = scope.getDirtyFiles();
        boolean cleanDroppedFiles = false;

        for (FilePath dirtyFile : dirtyFiles) {
          VirtualFile f = dirtyFile.getVirtualFile();
          if (f != null) {
            files.remove(f);
          }
          else {
            cleanDroppedFiles = true;
          }
        }
        if (cleanDroppedFiles) {
          for (Iterator<VirtualFile> iterator = files.iterator(); iterator.hasNext(); ) {
            final VirtualFile file = iterator.next();
            if (fileDropped(file)) {
              iterator.remove();
              scope.addDirtyFile(VcsUtil.getFilePath(file));
            }
          }
        }
      }
      else {
        for (Iterator<VirtualFile> iterator = files.iterator(); iterator.hasNext(); ) {
          final VirtualFile file = iterator.next();
          final boolean fileDropped = fileDropped(file);
          if (fileDropped) {
            scope.addDirtyFile(VcsUtil.getFilePath(file));
          }
          if (fileDropped || scope.belongsTo(VcsUtil.getFilePath(file))) {
            iterator.remove();
          }
        }
      }
    });
  }

  public void cleanAndAdjustScope(@NotNull final VcsModifiableDirtyScope scope) {
    cleanScope(myProject, myFiles, scope);
  }

  private static boolean fileDropped(final VirtualFile file) {
    return ! file.isValid();
  }

  public void addFile(VirtualFile file) {
    myFiles.add(file);
  }

  public void removeFile(VirtualFile file) {
    myFiles.remove(file);
  }

  // todo track number of copies made
  @NotNull
  public List<VirtualFile> getFiles() {
    return new ArrayList<>(myFiles);
  }

  public VirtualFileHolder copy() {
    final VirtualFileHolder copyHolder = new VirtualFileHolder(myProject, myType);
    copyHolder.myFiles.addAll(myFiles);
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
}
