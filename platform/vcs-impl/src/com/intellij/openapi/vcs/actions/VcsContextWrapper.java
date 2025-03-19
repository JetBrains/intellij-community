// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class VcsContextWrapper implements VcsContext {
  protected final @NotNull DataContext myContext;
  protected final int myModifiers;
  private final @NotNull String myPlace;
  private final @Nullable @NlsActions.ActionText String myActionName;

  public VcsContextWrapper(@NotNull DataContext context,
                           int modifiers,
                           @NotNull String place,
                           @Nullable @NlsActions.ActionText String actionName) {
    myContext = context;
    myModifiers = modifiers;
    myPlace = place;
    myActionName = actionName;
  }

  @Override
  public @NotNull String getPlace() {
    return myPlace;
  }

  @Override
  public @Nullable String getActionName() {
    return myActionName;
  }

  public static @NotNull VcsContext createCachedInstanceOn(@NotNull AnActionEvent event) {
    return new CachedVcsContext(createInstanceOn(event));
  }

  public static @NotNull VcsContextWrapper createInstanceOn(@NotNull AnActionEvent event) {
    return new VcsContextWrapper(event.getDataContext(), event.getModifiers(), event.getPlace(), event.getPresentation().getText());
  }

  @Override
  public @Nullable Project getProject() {
    return CommonDataKeys.PROJECT.getData(myContext);
  }

  @Override
  public @Nullable VirtualFile getSelectedFile() {
    return VcsContextUtil.selectedFilesIterable(myContext).first();
  }

  @Override
  public VirtualFile @NotNull [] getSelectedFiles() {
    return VcsContextUtil.selectedFilesIterable(myContext).toList().toArray(VirtualFile[]::new);
  }

  @Override
  public @NotNull Stream<VirtualFile> getSelectedFilesStream() {
    return StreamEx.of(VcsContextUtil.selectedFilesIterable(myContext).iterator());
  }

  @Override
  public @NotNull List<FilePath> getSelectedUnversionedFilePaths() {
    Iterable<FilePath> result = ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY.getData(myContext);

    return JBIterable.from(result).toList();
  }

  @Override
  public Editor getEditor() {
    return CommonDataKeys.EDITOR.getData(myContext);
  }

  @Override
  public Collection<VirtualFile> getSelectedFilesCollection() {
    return Arrays.asList(getSelectedFiles());
  }

  @Override
  public @Nullable File getSelectedIOFile() {
    FilePath filePath = getSelectedFilePath();
    return filePath != null ? filePath.getIOFile() : null;
  }

  @Override
  public File @Nullable [] getSelectedIOFiles() {
    return ContainerUtil.map(getSelectedFilePaths(), path -> path.getIOFile()).toArray(new File[0]);
  }

  @Override
  public int getModifiers() {
    return myModifiers;
  }

  @Override
  public @Nullable FilePath getSelectedFilePath() {
    return VcsContextUtil.selectedFilePathsIterable(myContext).first();
  }

  @Override
  public FilePath @NotNull [] getSelectedFilePaths() {
    return getSelectedFilePathsStream().toArray(FilePath[]::new);
  }

  @Override
  public @NotNull Stream<FilePath> getSelectedFilePathsStream() {
    return StreamEx.of(VcsContextUtil.selectedFilePathsIterable(myContext).iterator());
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
