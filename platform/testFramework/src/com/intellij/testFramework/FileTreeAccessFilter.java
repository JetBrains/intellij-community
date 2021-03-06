// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class FileTreeAccessFilter implements VirtualFileFilter {
  private final Set<VirtualFile> myAddedClasses = new HashSet<>();
  private boolean myTreeAccessAllowed;

  @Override
  public boolean accept(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWindow) return false;

    if (myAddedClasses.contains(file) || myTreeAccessAllowed) return false;

    FileType fileType = file.getFileType();
    return (fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS) && !file.getName().equals("package-info.java");
  }

  public void allowTreeAccessForFile(@NotNull VirtualFile file) {
    myAddedClasses.add(file);
  }

  public void allowTreeAccessForAllFiles() {
    myTreeAccessAllowed = true;
  }

  public String toString() {
    return "JAVA {allowed=" + myTreeAccessAllowed + " files=" + new ArrayList<>(myAddedClasses) + "}";
  }
}
