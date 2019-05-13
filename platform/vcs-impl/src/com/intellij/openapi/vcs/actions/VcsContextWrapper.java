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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.vcsUtil.VcsUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.util.containers.UtilKt.concat;
import static com.intellij.util.containers.UtilKt.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class VcsContextWrapper implements VcsContext {
  @NotNull protected final DataContext myContext;
  protected final int myModifiers;
  @NotNull private final String myPlace;
  @Nullable private final String myActionName;

  public VcsContextWrapper(@NotNull DataContext context, int modifiers, @NotNull String place, @Nullable String actionName) {
    myContext = context;
    myModifiers = modifiers;
    myPlace = place;
    myActionName = actionName;
  }

  @NotNull
  @Override
  public String getPlace() {
    return myPlace;
  }

  @Nullable
  @Override
  public String getActionName() {
    return myActionName;
  }

  @NotNull
  public static VcsContext createCachedInstanceOn(@NotNull AnActionEvent event) {
    return new CachedVcsContext(createInstanceOn(event));
  }

  @NotNull
  public static VcsContextWrapper createInstanceOn(@NotNull AnActionEvent event) {
    return new VcsContextWrapper(event.getDataContext(), event.getModifiers(), event.getPlace(), event.getPresentation().getText());
  }

  @Nullable
  @Override
  public Project getProject() {
    return CommonDataKeys.PROJECT.getData(myContext);
  }

  @Nullable
  @Override
  public VirtualFile getSelectedFile() {
    return getSelectedFilesStream().findFirst().orElse(null);
  }

  @NotNull
  @Override
  public VirtualFile[] getSelectedFiles() {
    VirtualFile[] fileArray = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(myContext);
    if (fileArray != null) {
      return Stream.of(fileArray).filter(VirtualFile::isInLocalFileSystem).toArray(VirtualFile[]::new);
    }

    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(myContext);
    return file != null && file.isInLocalFileSystem() ? new VirtualFile[]{file} : VirtualFile.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public Stream<VirtualFile> getSelectedFilesStream() {
    Stream<VirtualFile> result = VcsDataKeys.VIRTUAL_FILE_STREAM.getData(myContext);

    return result != null ? result.filter(VirtualFile::isInLocalFileSystem) : VcsContext.super.getSelectedFilesStream();
  }

  @NotNull
  @Override
  public List<VirtualFile> getSelectedUnversionedFiles() {
    Stream<VirtualFile> result = ChangesListView.UNVERSIONED_FILES_DATA_KEY.getData(myContext);

    return result != null ? result.collect(toList()) : emptyList();
  }

  @Override
  public Editor getEditor() {
    return CommonDataKeys.EDITOR.getData(myContext);
  }

  @Override
  public Collection<VirtualFile> getSelectedFilesCollection() {
    return Arrays.asList(getSelectedFiles());
  }

  @Nullable
  @Override
  public File getSelectedIOFile() {
    File file = VcsDataKeys.IO_FILE.getData(myContext);

    return file != null ? file : ArrayUtil.getFirstElement(VcsDataKeys.IO_FILE_ARRAY.getData(myContext));
  }

  @Nullable
  @Override
  public File[] getSelectedIOFiles() {
    File[] files = VcsDataKeys.IO_FILE_ARRAY.getData(myContext);
    if (!ArrayUtil.isEmpty(files)) return files;

    File file = VcsDataKeys.IO_FILE.getData(myContext);
    return file != null ? new File[]{file} : null;
  }

  @Override
  public int getModifiers() {
    return myModifiers;
  }

  @Override
  public Refreshable getRefreshableDialog() {
    return Refreshable.PANEL_KEY.getData(myContext);
  }

  @NotNull
  @Override
  public FilePath[] getSelectedFilePaths() {
    return getSelectedFilePathsStream().toArray(FilePath[]::new);
  }

  @NotNull
  @Override
  public Stream<FilePath> getSelectedFilePathsStream() {
    FilePath path = VcsDataKeys.FILE_PATH.getData(myContext);

    return concat(
      StreamEx.ofNullable(path),
      stream(VcsDataKeys.FILE_PATH_ARRAY.getData(myContext)),
      getSelectedFilesStream().map(VcsUtil::getFilePath),
      stream(getSelectedIOFiles()).map(VcsUtil::getFilePath)
    );
  }

  @Nullable
  @Override
  public FilePath getSelectedFilePath() {
    return ArrayUtil.getFirstElement(getSelectedFilePaths());
  }

  @Nullable
  @Override
  public ChangeList[] getSelectedChangeLists() {
    return VcsDataKeys.CHANGE_LISTS.getData(myContext);
  }

  @Nullable
  @Override
  public Change[] getSelectedChanges() {
    return VcsDataKeys.CHANGES.getData(myContext);
  }
}
