// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.fileTypes.FileTypesBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandlerBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.platform.backend.presentation.TargetPresentationBuilder;
import com.intellij.project.ProjectKt;
import com.intellij.project.ProjectStoreOwner;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApiStatus.Internal
@State(name = "ReadonlyStatusHandler", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ReadonlyStatusHandlerImpl extends ReadonlyStatusHandlerBase implements PersistentStateComponent<ReadonlyStatusHandlerImpl.State> {

  private final Project myProject;
  private boolean myClearReadOnlyInTests;

  public static final class State {
    public boolean SHOW_DIALOG = true;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      State state = (State)o;

      if (SHOW_DIALOG != state.SHOW_DIALOG) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return (SHOW_DIALOG ? 1 : 0);
    }
  }

  private State myState = new State();

  public ReadonlyStatusHandlerImpl(@NotNull Project project) {
    super(project);
    myProject = project;
  }

  @Override
  public @NotNull State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  @Override
  protected @NotNull OperationStatus ensureFilesWritable(@NotNull Collection<? extends VirtualFile> originalFiles, Collection<? extends VirtualFile> files) {
    IProjectStore stateStore = (myProject instanceof ProjectStoreOwner) ? ProjectKt.getStateStore(myProject) : null;
    Map<Boolean, List<FileInfo>> projectFilesAndOthersInfos = files.stream()
      .filter(vf-> vf != null && !vf.isWritable() && vf.isInLocalFileSystem())
      .map(vf -> new FileInfo(vf, myProject))
      .collect(Collectors.partitioningBy(info -> stateStore != null && stateStore.isProjectFile(info.getFile())));

    List<FileInfo> projectFiles = projectFilesAndOthersInfos.get(true);
    List<FileInfo> fileInfos = projectFilesAndOthersInfos.get(false);

    // if all files are already writable
    if (fileInfos.isEmpty() && projectFiles.isEmpty()) {
      return createResultStatus(originalFiles, files);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (myClearReadOnlyInTests) {
        ArrayList<FileInfo> allInfos = new ArrayList<>(fileInfos.size() + projectFiles.size());
        allInfos.addAll(fileInfos);
        allInfos.addAll(projectFiles);
        processFiles(allInfos, null, false);
      }
      return createResultStatus(originalFiles, files);
    }

    if (!projectFiles.isEmpty()) {
      processFiles(new ArrayList<>(projectFiles), null, false);
      if (fileInfos.isEmpty()) {
        return createResultStatus(originalFiles, files);
      }
    }

    // This event count hack is necessary to allow actions that called this stuff could still get data from their data contexts.
    // Otherwise data manager stuff will fire up an assertion saying that event count has been changed (due to modal dialog show-up)
    // The hack itself is safe since we guarantee that focus will return to the same component had it before modal dialog have been shown.
    final int savedEventCount = IdeEventQueue.getInstance().getEventCount();
    if (myState.SHOW_DIALOG && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      List<PresentableFileInfo> presentableFileInfos = ActionUtil.underModalProgress(
        myProject,
        FileTypesBundle.message("progress.title.resolving.filetype"),
        () -> createPresentableFileInfos(fileInfos)
      );
      new ReadOnlyStatusDialog(myProject, presentableFileInfos).show();
    }
    else {
      processFiles(new ArrayList<>(fileInfos), null, false); // the collection passed is modified
    }
    IdeEventQueue.getInstance().setEventCount(savedEventCount);
    return createResultStatus(originalFiles, files);
  }

  private List<PresentableFileInfo> createPresentableFileInfos(List<? extends FileInfo> fileInfos) {
    return ContainerUtil.map(fileInfos, fileInfo -> {
      TargetPresentationBuilder builder = TargetPresentation.builder(fileInfo.getFile().getPresentableName())
        .icon(VirtualFilePresentation.getIcon(fileInfo.getFile()))
        .presentableText(fileInfo.getFile().getPresentableName());
      VirtualFile vfParent = fileInfo.getFile().getParent();
      if (vfParent != null) builder = builder.locationText(vfParent.getPresentableUrl());
      return new PresentableFileInfo(fileInfo.getFile(), builder.presentation(), myProject);
    });
  }

  static void processFiles(@NotNull List<? extends FileInfo> fileInfos, @Nullable String changelist, boolean setChangeListActive) {
    FileInfo[] copy = fileInfos.toArray(new FileInfo[0]);
    MultiMap<HandleType, VirtualFile> handleTypeToFile = new MultiMap<>();
    for (FileInfo fileInfo : copy) {
      handleTypeToFile.putValue(fileInfo.getSelectedHandleType(), fileInfo.getFile());
    }

    for (HandleType handleType : handleTypeToFile.keySet()) {
      handleType.processFiles(handleTypeToFile.get(handleType), changelist, setChangeListActive);
    }

    for (FileInfo fileInfo : copy) {
      if (!fileInfo.getFile().exists() || fileInfo.getFile().isWritable()) {
        fileInfos.remove(fileInfo);
      }
    }
  }

  /**
   * Normally when file is read-only and ensureFilesWritable is called, a dialog box appears which allows user to decide
   * whether to clear read-only flag or not. This method allows to control what will happen in unit-test mode.
   *
   * @param clearReadOnlyInTests if true, ensureFilesWritable will try to clear read-only status from passed files.
   *                         Otherwise, read-only status is not modified (as if user refused to modify it).
   */
  @TestOnly
  public void setClearReadOnlyInTests(boolean clearReadOnlyInTests) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myClearReadOnlyInTests = clearReadOnlyInTests;
  }

}
