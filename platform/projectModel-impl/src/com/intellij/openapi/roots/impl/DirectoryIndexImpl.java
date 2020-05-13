// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.Query;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class DirectoryIndexImpl extends DirectoryIndex implements Disposable {
  private static final Logger LOG = Logger.getInstance(DirectoryIndexImpl.class);

  private final Project myProject;
  private final MessageBusConnection myConnection;

  private volatile boolean myDisposed;
  private volatile RootIndex myRootIndex;

  public DirectoryIndexImpl(@NotNull Project project) {
    myProject = project;
    myConnection = project.getMessageBus().connect();
    subscribeToFileChanges();
    LowMemoryWatcher.register(() -> {
      RootIndex index = myRootIndex;
      if (index != null) {
        index.onLowMemory();
      }
    }, this);
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myRootIndex = null;
  }

  private void subscribeToFileChanges() {
    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void beforeRootsChange(@NotNull ModuleRootEvent event) {
        myRootIndex = null;
      }

      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        myRootIndex = null;
      }
    });

    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        RootIndex rootIndex = myRootIndex;
        if (rootIndex != null && shouldResetOnEvents(events)) {
          rootIndex.myPackageDirectoryCache.clear();
          for (VFileEvent event : events) {
            if (isIgnoredFileCreated(event)) {
              myRootIndex = null;
              break;
            }
          }
        }
      }
    });
  }

  private static boolean shouldResetOnEvents(@NotNull List<? extends VFileEvent> events) {
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

  private static boolean isIgnoredFileCreated(@NotNull VFileEvent event) {
    return event instanceof VFileMoveEvent && FileTypeRegistry.getInstance().isFileIgnored(((VFileMoveEvent)event).getNewParent()) ||
           event instanceof VFilePropertyChangeEvent &&
           ((VFilePropertyChangeEvent)event).getPropertyName().equals(VirtualFile.PROP_NAME) &&
           FileTypeRegistry.getInstance().isFileIgnored(((VFilePropertyChangeEvent)event).getFile());
  }

  private void dispatchPendingEvents() {
    myConnection.deliverImmediately();
  }

  @Override
  @NotNull
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return getRootIndex().getDirectoriesByPackageName(packageName, includeLibrarySources);
  }

  @NotNull
  private RootIndex getRootIndex() {
    RootIndex rootIndex = myRootIndex;
    if (rootIndex == null) {
      myRootIndex = rootIndex = new RootIndex(myProject);
    }
    return rootIndex;
  }

  @NotNull
  @Override
  public DirectoryInfo getInfoForFile(@NotNull VirtualFile file) {
    checkAvailability();
    dispatchPendingEvents();

    if (!(file instanceof VirtualFileWithId)) return NonProjectDirectoryInfo.NOT_SUPPORTED_VIRTUAL_FILE_IMPLEMENTATION;

    return getRootIndex().getInfoForFile(file);
  }

  @Nullable
  @Override
  public SourceFolder getSourceRootFolder(@NotNull DirectoryInfo info) {
    boolean inModuleSource = info instanceof DirectoryInfoImpl && ((DirectoryInfoImpl)info).isInModuleSource();
    if (inModuleSource) {
      return info.getSourceRootFolder();
    }
    return null;
  }

  @Override
  @Nullable
  public JpsModuleSourceRootType<?> getSourceRootType(@NotNull DirectoryInfo info) {
    SourceFolder folder = getSourceRootFolder(info);
    return folder == null ? null : folder.getRootType();
  }

  @Override
  public String getPackageName(@NotNull VirtualFile dir) {
    checkAvailability();
    if (!(dir instanceof VirtualFileWithId)) return null;

    return getRootIndex().getPackageName(dir);
  }

  @NotNull
  @Override
  public List<OrderEntry> getOrderEntries(@NotNull DirectoryInfo info) {
    checkAvailability();
    if (myProject.isDefault()) return Collections.emptyList();
    return getRootIndex().getOrderEntries(info);
  }

  @Override
  @NotNull
  public Set<String> getDependentUnloadedModules(@NotNull Module module) {
    checkAvailability();
    return getRootIndex().getDependentUnloadedModules(module);
  }

  @TestOnly
  public void assertConsistency(DirectoryInfo info) {
    List<OrderEntry> entries = getOrderEntries(info);
    for (int i = 1; i < entries.size(); i++) {
      assert RootIndex.BY_OWNER_MODULE.compare(entries.get(i - 1), entries.get(i)) <= 0;
    }
  }

  private void checkAvailability() {
    if (myDisposed) {
      ProgressManager.checkCanceled();
      LOG.error("Directory index is already disposed for " + myProject);
    }
  }
}
