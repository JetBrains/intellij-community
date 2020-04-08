// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public VirtualFile @NotNull [] getSelectedFiles() {
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
  public List<FilePath> getSelectedUnversionedFilePaths() {
    Stream<FilePath> result = ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY.getData(myContext);

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

  @Override
  public File @Nullable [] getSelectedIOFiles() {
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

  @Override
  public FilePath @NotNull [] getSelectedFilePaths() {
    return getSelectedFilePathsStream().toArray(FilePath[]::new);
  }

  @NotNull
  @Override
  public Stream<FilePath> getSelectedFilePathsStream() {
    FilePath path = VcsDataKeys.FILE_PATH.getData(myContext);
    Stream<FilePath> pathStream = VcsDataKeys.FILE_PATH_STREAM.getData(myContext);

    return concat(
      StreamEx.ofNullable(path),
      pathStream != null ? pathStream : getSelectedFilesStream().map(VcsUtil::getFilePath),
      stream(getSelectedIOFiles()).map(VcsUtil::getFilePath)
    ).distinct();
  }

  @Nullable
  @Override
  public FilePath getSelectedFilePath() {
    return ArrayUtil.getFirstElement(getSelectedFilePaths());
  }

  @Override
  public ChangeList @Nullable [] getSelectedChangeLists() {
    return VcsDataKeys.CHANGE_LISTS.getData(myContext);
  }

  @Override
  public Change @Nullable [] getSelectedChanges() {
    return VcsDataKeys.CHANGES.getData(myContext);
  }
}
