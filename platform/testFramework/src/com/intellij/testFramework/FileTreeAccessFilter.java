/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author cdr
 */
public class FileTreeAccessFilter implements VirtualFileFilter {
  private final Set<VirtualFile> myAddedClasses = new THashSet<>();
  private boolean myTreeAccessAllowed;

  @Override
  public boolean accept(VirtualFile file) {
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
