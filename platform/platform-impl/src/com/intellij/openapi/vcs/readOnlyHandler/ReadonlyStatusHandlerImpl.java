// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.ide.IdeBundle;
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

@State(name = "ReadonlyStatusHandler", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ReadonlyStatusHandlerImpl extends ReadonlyStatusHandler implements PersistentStateComponent<ReadonlyStatusHandlerImpl.State> {
  private static final Logger LOG = Logger.getInstance(ReadonlyStatusHandlerImpl.class);

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
    myProject = project;
  }

  @Override
  @NotNull
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  @NotNull
  @Override
  public OperationStatus ensureFilesWritable(@NotNull Collection<? extends VirtualFile> originalFiles) {
    if (originalFiles.isEmpty()) {
      return new OperationStatusImpl(VirtualFile.EMPTY_ARRAY);
    }

    checkThreading();

    Set<VirtualFile> realFiles = new HashSet<>(originalFiles.size());
    for (VirtualFile file : originalFiles) {
      if (file instanceof LightVirtualFile) {
        VirtualFile originalFile = ((LightVirtualFile)file).getOriginalFile();
        if (originalFile != null) {
          file = originalFile;
        }
      }
      if (file instanceof VirtualFileWindow) {
        file = ((VirtualFileWindow)file).getDelegate();
      }
      if (file != null) {
        realFiles.add(file);
      }
    }
    Collection<? extends VirtualFile> files = new ArrayList<>(realFiles);

    if (!myProject.isDefault()) {
      OperationStatusImpl status = WritingAccessProvider.EP.computeSafeIfAny(myProject, provider -> {
        Collection<VirtualFile> denied = ContainerUtil.filter(files, virtualFile -> !provider.isPotentiallyWritable(virtualFile));

        if (denied.isEmpty()) {
          denied = provider.requestWriting(files);
        }
        if (!denied.isEmpty()) {
          return new OperationStatusImpl(VfsUtilCore.toVirtualFileArray(denied), provider.getReadOnlyMessage());
        }
        return null;
      });
      if (status != null) {
        return status;
      }
    }

    final List<FileInfo> fileInfos = createFileInfos(files);
    // if all files are already writable
    if (fileInfos.isEmpty()) {
      return createResultStatus(originalFiles, files);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (myClearReadOnlyInTests) {
        processFiles(new ArrayList<>(fileInfos), null);
      }
      return createResultStatus(originalFiles, files);
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
    return createResultStatus(originalFiles, files);
  }

  private static void checkThreading() {
    Application app = ApplicationManager.getApplication();
    app.assertIsWriteThread();
    if (!app.isWriteAccessAllowed()) return;

    if (app.isUnitTestMode() && Registry.is("tests.assert.clear.read.only.status.outside.write.action")) {
      LOG.error("ensureFilesWritable should be called outside write action");
    }
  }

  private static OperationStatus createResultStatus(@NotNull Collection<? extends VirtualFile> originalFiles,
                                                    @NotNull Collection<? extends VirtualFile> files) {
    List<VirtualFile> readOnlyFiles = new ArrayList<>();
    for (VirtualFile file : files) {
      if (file.exists()) {
        if (!file.isWritable()) {
          readOnlyFiles.add(file);
        }
      }
    }

    // we shouldn't report success if files for which write operation is requested are still non-writable
    assert !readOnlyFiles.isEmpty() || originalFiles.stream().allMatch(file -> file == null || file.isWritable())
      : "Original files: " + originalFiles + ", files: " + files;

    return new OperationStatusImpl(VfsUtilCore.toVirtualFileArray(readOnlyFiles));
  }

  @NotNull
  private List<FileInfo> createFileInfos(@NotNull Collection<? extends VirtualFile> files) {
    List<FileInfo> fileInfos = new ArrayList<>();
    for (final VirtualFile file : files) {
      if (file != null && !file.isWritable() && file.isInLocalFileSystem()) {
        fileInfos.add(new FileInfo(file, myProject));
      }
    }
    return fileInfos;
  }

  public static void processFiles(@NotNull List<FileInfo> fileInfos, @Nullable String changelist) {
    FileInfo[] copy = fileInfos.toArray(new FileInfo[0]);
    MultiMap<HandleType, VirtualFile> handleTypeToFile = new MultiMap<>();
    for (FileInfo fileInfo : copy) {
      handleTypeToFile.putValue(fileInfo.getSelectedHandleType(), fileInfo.getFile());
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

  private static final class OperationStatusImpl extends OperationStatus {
    private final VirtualFile[] myReadonlyFiles;
    @NotNull private final String myReadOnlyReason;

    OperationStatusImpl(VirtualFile @NotNull [] readonlyFiles) {
      this(readonlyFiles,"");
    }

    private OperationStatusImpl(VirtualFile[] readonlyFiles, @NotNull String readOnlyReason) {
      myReadonlyFiles = readonlyFiles;
      myReadOnlyReason = readOnlyReason;
    }

    @Override
    public VirtualFile @NotNull [] getReadonlyFiles() {
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
        if (!Strings.isEmpty(myReadOnlyReason)) {
          return myReadOnlyReason;
        }
        if (myReadonlyFiles.length > 1) {
          StringBuilder buf = new StringBuilder();
          for (VirtualFile file : myReadonlyFiles) {
            buf.append('\n');
            buf.append(file.getPresentableUrl());
          }

          return IdeBundle.message("failed.to.make.the.following.files.writable.error.message", buf.toString());
        }
        else {
          return IdeBundle.message("failed.to.make.file.writable.error.message", myReadonlyFiles[0].getPresentableUrl());
        }
      }
      throw new RuntimeException("No readonly files");
    }
  }
}