// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;
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
  @NotNull protected final DataContext myContext;
  protected final int myModifiers;
  @NotNull private final String myPlace;
  @Nullable private final @NlsActions.ActionText String myActionName;

  public VcsContextWrapper(@NotNull DataContext context,
                           int modifiers,
                           @NotNull String place,
                           @Nullable @NlsActions.ActionText String actionName) {
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
    return VcsContextUtil.selectedFilesIterable(myContext).first();
  }

  @Override
  public VirtualFile @NotNull [] getSelectedFiles() {
    return VcsContextUtil.selectedFilesIterable(myContext).toList().toArray(VirtualFile[]::new);
  }

  @NotNull
  @Override
  public Stream<VirtualFile> getSelectedFilesStream() {
    return StreamEx.of(VcsContextUtil.selectedFilesIterable(myContext).iterator());
  }

  @NotNull
  @Override
  public List<FilePath> getSelectedUnversionedFilePaths() {
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

  @Nullable
  @Override
  public File getSelectedIOFile() {
    return VcsContextUtil.selectedIOFilesIterable(myContext).first();
  }

  @Override
  public File @Nullable [] getSelectedIOFiles() {
    return VcsContextUtil.selectedIOFilesIterable(myContext).toList().toArray(new File[0]);
  }

  @Override
  public int getModifiers() {
    return myModifiers;
  }

  @Override
  public Refreshable getRefreshableDialog() {
    return Refreshable.PANEL_KEY.getData(myContext);
  }

  @Nullable
  @Override
  public FilePath getSelectedFilePath() {
    return VcsContextUtil.selectedFilePathsIterable(myContext).first();
  }

  @Override
  public FilePath @NotNull [] getSelectedFilePaths() {
    return getSelectedFilePathsStream().toArray(FilePath[]::new);
  }

  @NotNull
  @Override
  public Stream<FilePath> getSelectedFilePathsStream() {
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
