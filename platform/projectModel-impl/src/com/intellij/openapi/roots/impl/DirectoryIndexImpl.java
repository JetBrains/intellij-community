// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Query;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * This is an internal class, {@link DirectoryIndex} must be used instead.
 */
@ApiStatus.Internal
public final class DirectoryIndexImpl extends DirectoryIndex implements Disposable {
  private static final Logger LOG = Logger.getInstance(DirectoryIndexImpl.class);

  private final Project myProject;
  private final MessageBusConnection myConnection;
  private final WorkspaceFileIndexEx myWorkspaceFileIndex;

  private volatile boolean myDisposed;
  private volatile RootIndex myRootIndex;

  public DirectoryIndexImpl(@NotNull Project project) {
    myWorkspaceFileIndex = (WorkspaceFileIndexEx)WorkspaceFileIndex.getInstance(project);
    myProject = project;
    myConnection = project.getMessageBus().connect();
    subscribeToFileChanges();
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myRootIndex = null;
  }

  private void subscribeToFileChanges() {
    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        RootIndex rootIndex = myRootIndex;
        if (rootIndex != null && shouldResetOnEvents(events)) {
          for (VFileEvent event : events) {
            if (isIgnoredFileCreated(event)) {
              reset();
              break;
            }
          }
        }
      }
    });
  }

  public static boolean shouldResetOnEvents(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      // VFileCreateEvent.getFile() is expensive
      if (event instanceof VFileCreateEvent) {
        if (((VFileCreateEvent)event).isDirectory()) return true;
      }
      else {
        VirtualFile file = event.getFile();
        if (file == null || file.isDirectory()) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isIgnoredFileCreated(@NotNull VFileEvent event) {
    return event instanceof VFileMoveEvent && FileTypeRegistry.getInstance().isFileIgnored(((VFileMoveEvent)event).getNewParent()) ||
           event instanceof VFilePropertyChangeEvent &&
           ((VFilePropertyChangeEvent)event).getPropertyName().equals(VirtualFile.PROP_NAME) &&
           FileTypeRegistry.getInstance().isFileIgnored(((VFilePropertyChangeEvent)event).getFile());
  }

  @Override
  public @NotNull Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return myWorkspaceFileIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
  }

  @Override
  public @NotNull Query<VirtualFile> getFilesByPackageName(@NotNull String packageName) {
    return myWorkspaceFileIndex.getFilesByPackageName(packageName);
  }

  @Override
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName,
                                                        @NotNull GlobalSearchScope scope) {
    return myWorkspaceFileIndex.getDirectoriesByPackageName(packageName, scope);
  }

  private RootIndex getRootIndex() {
    RootIndex rootIndex = myRootIndex;
    if (rootIndex == null) {
      myRootIndex = rootIndex = new RootIndex(myProject);
    }
    return rootIndex;
  }

  @Override
  public String getPackageName(@NotNull VirtualFile dir) {
    checkAvailability();
    return myWorkspaceFileIndex.getPackageName(dir);
  }

  @Override
  public @NotNull List<OrderEntry> getOrderEntries(@NotNull VirtualFile fileOrDir) {
    checkAvailability();
    if (myProject.isDefault()) return Collections.emptyList();
    WorkspaceFileInternalInfo fileInfo = myWorkspaceFileIndex.getFileInfo(fileOrDir, true, true, true, true, false);
    WorkspaceFileSetWithCustomData<?> fileSet = fileInfo.findFileSet(data -> true);
    if (fileSet == null) return Collections.emptyList();
    return getRootIndex().getOrderEntries(fileSet.getRoot());
  }

  @Override
  public @NotNull Set<String> getDependentUnloadedModules(@NotNull Module module) {
    checkAvailability();
    return getRootIndex().getDependentUnloadedModules(module);
  }

  private void checkAvailability() {
    ThreadingAssertions.assertReadAccess();
    if (myDisposed) {
      ProgressManager.checkCanceled();
      LOG.error("Directory index is already disposed for " + myProject);
    }
  }

  @ApiStatus.Internal
  public void reset() {
    myRootIndex = null;
  }
}
