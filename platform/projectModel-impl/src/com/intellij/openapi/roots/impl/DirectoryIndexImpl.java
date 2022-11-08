// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.model.ModelBranch;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.CollectionQuery;
import com.intellij.util.Query;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collection;
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
    myWorkspaceFileIndex = WorkspaceFileIndexEx.IS_ENABLED ? (WorkspaceFileIndexEx)WorkspaceFileIndex.getInstance(project) : null;
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
    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        RootIndex rootIndex = myRootIndex;
        if (rootIndex != null && shouldResetOnEvents(events)) {
          rootIndex.myPackageDirectoryCache.clear();
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

  private void dispatchPendingEvents() {
    myConnection.deliverImmediately();
  }

  @Override
  @NotNull
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    if (myWorkspaceFileIndex != null) {
      return myWorkspaceFileIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
    }
    return getRootIndex().getDirectoriesByPackageName(packageName, includeLibrarySources);
  }

  @Override
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName,
                                                        @NotNull GlobalSearchScope scope) {
    if (myWorkspaceFileIndex != null) {
      return myWorkspaceFileIndex.getDirectoriesByPackageName(packageName, scope);
    }
    
    Collection<ModelBranch> branches = scope.getModelBranchesAffectingScope();
    if (branches.isEmpty()) {
      return super.getDirectoriesByPackageName(packageName, scope);
    }

    List<RootIndex> indices = ContainerUtil.map(branches, DirectoryIndexImpl::obtainBranchRootIndex);
    indices.add(getRootIndex());
    return new CollectionQuery<>(indices)
      .flatMapping(i -> i.getDirectoriesByPackageName(packageName, true))
      .filtering(scope::contains);
  }

  @NotNull
  RootIndex getRootIndex(VirtualFile file) {
    ModelBranch branch = ModelBranch.getFileBranch(file);
    if (branch != null) {
      return obtainBranchRootIndex(branch);
    }
    return getRootIndex();
  }

  private static final Key<Pair<Long, RootIndex>> BRANCH_ROOT_INDEX = Key.create("BRANCH_ROOT_INDEX");

  private static RootIndex obtainBranchRootIndex(ModelBranch branch) {
    Pair<Long, RootIndex> pair = branch.getUserData(BRANCH_ROOT_INDEX);
    long modCount = branch.getBranchedVfsStructureModificationCount();
    if (pair == null || pair.first != modCount) {
      pair = Pair.create(modCount, new RootIndex(branch.getProject(), RootFileSupplier.forBranch(branch)));
      branch.putUserData(BRANCH_ROOT_INDEX, pair);
    }
    return pair.second;
  }

  RootIndex getRootIndex() {
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
    ProgressManager.checkCanceled();
    SlowOperations.assertSlowOperationsAreAllowed();
    dispatchPendingEvents();
    return getRootIndex(file).getInfoForFile(file);
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
    if (myWorkspaceFileIndex != null) {
      return myWorkspaceFileIndex.getPackageName(dir);
    } 

    return getRootIndex(dir).getPackageName(dir);
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
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (myDisposed) {
      ProgressManager.checkCanceled();
      LOG.error("Directory index is already disposed for " + myProject);
    }
  }

  void reset() {
    myRootIndex = null;
  }
}
