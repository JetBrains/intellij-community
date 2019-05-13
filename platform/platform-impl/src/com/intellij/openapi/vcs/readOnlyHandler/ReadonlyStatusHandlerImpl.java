// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@State(name = "ReadonlyStatusHandler", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ReadonlyStatusHandlerImpl extends ReadonlyStatusHandler implements PersistentStateComponent<ReadonlyStatusHandlerImpl.State> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl");
  private final Project myProject;
  private final WritingAccessProvider[] myAccessProviders;
  protected boolean myClearReadOnlyInTests;

  public static class State {
    public boolean SHOW_DIALOG = true;
  }

  private State myState = new State();

  public ReadonlyStatusHandlerImpl(Project project) {
    myProject = project;
    myAccessProviders = WritingAccessProvider.getProvidersForProject(myProject);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  @NotNull
  @Override
  public OperationStatus ensureFilesWritable(@NotNull Collection<VirtualFile> files) {
    if (files.isEmpty()) {
      return new OperationStatusImpl(VirtualFile.EMPTY_ARRAY);
    }

    checkThreading();

    Set<VirtualFile> realFiles = new THashSet<>(files.size());
    for (VirtualFile file : files) {
      if (file instanceof LightVirtualFile) {
        VirtualFile originalFile = ((LightVirtualFile)file).getOriginalFile();
        if (originalFile != null) {
          file = originalFile;
        }
      }
      if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
      if (file != null) {
        realFiles.add(file);
      }
    }
    files = new ArrayList<>(realFiles);

    for (final WritingAccessProvider accessProvider : myAccessProviders) {
      Collection<VirtualFile> denied = ContainerUtil.filter(files, virtualFile -> !accessProvider.isPotentiallyWritable(virtualFile));

      if (denied.isEmpty()) {
        denied = accessProvider.requestWriting(files);
      }
      if (!denied.isEmpty()) {
        return new OperationStatusImpl(VfsUtilCore.toVirtualFileArray(denied));
      }
    }

    final List<FileInfo> fileInfos = createFileInfos(files);
    // if all files are already writable
    if (fileInfos.isEmpty()) {
      return createResultStatus(files);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (myClearReadOnlyInTests) {
        processFiles(new ArrayList<>(fileInfos), null);
      }
      return createResultStatus(files);
    }

    // This event count hack is necessary to allow actions that called this stuff could still get data from their data contexts.
    // Otherwise data manager stuff will fire up an assertion saying that event count has been changed (due to modal dialog show-up)
    // The hack itself is safe since we guarantee that focus will return to the same component had it before modal dialog have been shown.
    final int savedEventCount = IdeEventQueue.getInstance().getEventCount();
    if (myState.SHOW_DIALOG) {
      new ReadOnlyStatusDialog(myProject, fileInfos).show();
    }
    else {
      processFiles(new ArrayList<>(fileInfos), null); // the collection passed is modified
    }
    IdeEventQueue.getInstance().setEventCount(savedEventCount);
    return createResultStatus(files);
  }

  private static void checkThreading() {
    Application app = ApplicationManager.getApplication();
    app.assertIsDispatchThread();
    if (!app.isWriteAccessAllowed()) return;

    if (app.isUnitTestMode() && Registry.is("tests.assert.clear.read.only.status.outside.write.action")) {
      LOG.error("ensureFilesWritable should be called outside write action");
    }
  }

  private static OperationStatus createResultStatus(@NotNull Collection<VirtualFile> files) {
    List<VirtualFile> readOnlyFiles = new ArrayList<>();
    for (VirtualFile file : files) {
      if (file.exists()) {
        if (!file.isWritable()) {
          readOnlyFiles.add(file);
        }
      }
    }

    return new OperationStatusImpl(VfsUtilCore.toVirtualFileArray(readOnlyFiles));
  }

  @NotNull
  private List<FileInfo> createFileInfos(@NotNull Collection<VirtualFile> files) {
    List<FileInfo> fileInfos = new ArrayList<>();
    for (final VirtualFile file : files) {
      if (file != null && !file.isWritable() && file.isInLocalFileSystem()) {
        fileInfos.add(new FileInfo(file, myProject));
      }
    }
    return fileInfos;
  }

  public static void processFiles(final List<FileInfo> fileInfos, @Nullable String changelist) {
    FileInfo[] copy = fileInfos.toArray(new FileInfo[0]);
    MultiValuesMap<HandleType, VirtualFile> handleTypeToFile = new MultiValuesMap<>();
    for (FileInfo fileInfo : copy) {
      handleTypeToFile.put(fileInfo.getSelectedHandleType(), fileInfo.getFile());
    }

    for (HandleType handleType : handleTypeToFile.keySet()) {
      handleType.processFiles(handleTypeToFile.get(handleType), changelist);
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

  private static class OperationStatusImpl extends OperationStatus {

    private final VirtualFile[] myReadonlyFiles;

    OperationStatusImpl(@NotNull VirtualFile[] readonlyFiles) {
      myReadonlyFiles = readonlyFiles;
    }

    @Override
    @NotNull
    public VirtualFile[] getReadonlyFiles() {
      return myReadonlyFiles;
    }

    @Override
    public boolean hasReadonlyFiles() {
      return myReadonlyFiles.length > 0;
    }

    @Override
    @NotNull
    public String getReadonlyFilesMessage() {
      if (hasReadonlyFiles()) {
        StringBuilder buf = new StringBuilder();
        if (myReadonlyFiles.length > 1) {
          for (VirtualFile file : myReadonlyFiles) {
            buf.append('\n');
            buf.append(file.getPresentableUrl());
          }

          return CommonBundle.message("failed.to.make.the.following.files.writable.error.message", buf.toString());
        }
        else {
          return CommonBundle.message("failed.to.make.file.writeable.error.message", myReadonlyFiles[0].getPresentableUrl());
        }
      }
      throw new RuntimeException("No readonly files");
    }
  }
}
