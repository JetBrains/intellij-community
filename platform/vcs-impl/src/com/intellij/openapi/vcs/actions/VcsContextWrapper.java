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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class VcsContextWrapper implements VcsContext {
  protected final DataContext myContext;
  protected final int myModifiers;
  private final String myPlace;

  public VcsContextWrapper(DataContext context, int modifiers, String place) {
    myContext = context;
    myModifiers = modifiers;
    myPlace = place;
  }

  public String getPlace() {
    return myPlace;
  }

  public static VcsContext createCachedInstanceOn(AnActionEvent event) {
    return new CachedVcsContext(createInstanceOn(event));
  }

  public static VcsContextWrapper createInstanceOn(final AnActionEvent event) {
    return new VcsContextWrapper(event.getDataContext(), event.getModifiers(), event.getPlace());
  }

  public Project getProject() {
    return PlatformDataKeys.PROJECT.getData(myContext);
  }

  public VirtualFile getSelectedFile() {
    VirtualFile[] files = getSelectedFiles();
    if (files == null || files.length == 0) return null;
    return files[0];
  }

  public VirtualFile[] getSelectedFiles() {
    VirtualFile[] fileArray = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(myContext);
    if (fileArray != null) {
      return filterLocalFiles(fileArray);
    }

    VirtualFile virtualFile = PlatformDataKeys.VIRTUAL_FILE.getData(myContext);
    if (virtualFile != null && isLocal(virtualFile)) {
      return new VirtualFile[]{virtualFile};
    }

    return VirtualFile.EMPTY_ARRAY;
  }

  private static boolean isLocal(VirtualFile virtualFile) {
    return virtualFile.isInLocalFileSystem();
  }

  private static VirtualFile[] filterLocalFiles(VirtualFile[] fileArray) {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile virtualFile : fileArray) {
      if (isLocal(virtualFile)) {
        result.add(virtualFile);
      }
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  public Editor getEditor() {
    return PlatformDataKeys.EDITOR.getData(myContext);
  }

  public Collection<VirtualFile> getSelectedFilesCollection() {
    return Arrays.asList(getSelectedFiles());
  }

  public File getSelectedIOFile() {
    File file = VcsDataKeys.IO_FILE.getData(myContext);
    if (file != null) return file;
    File[] files = VcsDataKeys.IO_FILE_ARRAY.getData(myContext);
    if (files == null) return null;
    if (files.length == 0) return null;
    return files[0];
  }

  public File[] getSelectedIOFiles() {
    File[] files = VcsDataKeys.IO_FILE_ARRAY.getData(myContext);
    if (files != null && files.length > 0) return files;
    File file = getSelectedIOFile();
    if (file != null) return new File[]{file};
    return null;
  }

  public int getModifiers() {
    return myModifiers;
  }

  public Refreshable getRefreshableDialog() {
    return Refreshable.PANEL_KEY.getData(myContext);
  }

  public FilePath[] getSelectedFilePaths() {
    Set<FilePath> result = new HashSet<FilePath>();
    FilePath path = VcsDataKeys.FILE_PATH.getData(myContext);
    if (path != null) {
      result.add(path);
    }

    FilePath[] paths = VcsDataKeys.FILE_PATH_ARRAY.getData(myContext);
    if (paths != null) {
      for (FilePath filePath : paths) {
        if (!result.contains(filePath)) {
          result.add(filePath);
        }
      }
    }

    VirtualFile[] selectedFiles = getSelectedFiles();
    if (selectedFiles != null) {
      for (VirtualFile selectedFile : selectedFiles) {
        FilePathImpl filePath = new FilePathImpl(selectedFile);
        result.add(filePath);
      }
    }

    File[] selectedIOFiles = getSelectedIOFiles();
    if (selectedIOFiles != null){
      for (File selectedFile : selectedIOFiles) {
        FilePathImpl filePath = FilePathImpl.create(selectedFile);
        if (filePath != null) {
          result.add(filePath);
        }
      }

    }

    return result.toArray(new FilePath[result.size()]);

  }

  @Nullable
  public FilePath getSelectedFilePath() {
    FilePath[] selectedFilePaths = getSelectedFilePaths();
    if (selectedFilePaths.length == 0) {
      return null;
    }
    else {
      return selectedFilePaths[0];
    }
  }

  @Nullable
  public ChangeList[] getSelectedChangeLists() {
    return VcsDataKeys.CHANGE_LISTS.getData(myContext);
  }

  @Nullable
  public Change[] getSelectedChanges() {
    return VcsDataKeys.CHANGES.getData(myContext);
  }
}
