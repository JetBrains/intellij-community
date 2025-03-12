// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.platform.workspace.storage.EntityPointer;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData;
import com.intellij.workspaceModel.core.fileIndex.impl.*;
import kotlin.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

/**
 * This is an internal class, {@link ModuleFileIndex} must be used instead.
 */
@ApiStatus.Internal
public class ModuleFileIndexImpl extends FileIndexBase implements ModuleFileIndex {
  private final @NotNull Module myModule;

  public ModuleFileIndexImpl(@NotNull Module module) {
    super(module.getProject());

    myModule = module;
  }

  @Override
  public boolean iterateContent(@NotNull ContentIterator processor, @Nullable VirtualFileFilter filter) {
    Pair<Collection<VirtualFile>, Collection<VirtualFile>> rootsPair = ReadAction.nonBlocking(() -> {
      Project project = myModule.getProject();
      if (project.isDisposed()) return null;
      WorkspaceFileIndexEx index = (WorkspaceFileIndexEx)WorkspaceFileIndex.getInstance(project);
      Collection<VirtualFile> recursiveRoots = new HashSet<>();
      Collection<VirtualFile> nonRecursiveRoots = new SmartList<>();
      index.visitFileSets(new WorkspaceFileSetVisitor() {
        private int visitedCount = 0;

        @Override
        public void visitIncludedRoot(@NotNull WorkspaceFileSet fileSet,
                                      @NotNull EntityPointer<? extends @NotNull WorkspaceEntity> entityPointer) {
          visitedCount++;
          if (visitedCount % 100 == 0) {
            ProgressManager.checkCanceled();
          }
          if (!(fileSet instanceof WorkspaceFileSetWithCustomData<?>) || !isInContent((WorkspaceFileSetWithCustomData<?>)fileSet)) {
            return;
          }
          VirtualFile root = fileSet.getRoot();
          if (fileSet instanceof WorkspaceFileSetImpl && !((WorkspaceFileSetImpl)fileSet).getRecursive()) {
            nonRecursiveRoots.add(fileSet.getRoot());
          }
          else {
            recursiveRoots.add(root);
          }
        }
      });
      Collection<VirtualFile> filteredRecursiveRoots = ContainerUtil.filter(recursiveRoots, root -> !isNestedRootOfModuleContent(root));
      return new Pair<>(filteredRecursiveRoots, nonRecursiveRoots);
    }).executeSynchronously();
    if (rootsPair == null) return false; // project is disposed
    return iterateProvidedRootsOfContent(processor, filter, rootsPair.getFirst(), rootsPair.getSecond());
  }

  private boolean isNestedRootOfModuleContent(@NotNull VirtualFile root) {
    VirtualFile parent = root.getParent();
    if (parent == null) {
      return false;
    }
    WorkspaceFileInternalInfo fileInfo = myWorkspaceFileIndex.getFileInfo(parent, false, true, false, false, false);
    return fileInfo.findFileSet(this::hasRecursiveRootFromModuleContent) != null;
  }

  private boolean hasRecursiveRootFromModuleContent(@NotNull WorkspaceFileSetWithCustomData<?> fileSet) {
    if (!fileSet.getRecursive()) {
      return false;
    }
    return isInContent(fileSet);
  }

  @Override
  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSetWithCustomData<ModuleRelatedRootData> fileSet =
      myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, false, false, ModuleRelatedRootData.class);
    return isFromThisModule(fileSet);
  }

  private boolean isFromThisModule(@Nullable WorkspaceFileSetWithCustomData<? extends ModuleRelatedRootData> fileSet) {
    return fileSet != null && fileSet.getData().getModule().equals(myModule);
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, false, false, ModuleSourceRootData.class);
    return fileSet != null && isFromThisModule(fileSet);
  }

  @Override
  public @NotNull List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile fileOrDir) {
    return findAllOrderEntriesWithOwnerModule(myModule, myDirectoryIndex.getOrderEntries(fileOrDir));
  }

  @Override
  public OrderEntry getOrderEntryForFile(@NotNull VirtualFile fileOrDir) {
    return findOrderEntryWithOwnerModule(myModule, myDirectoryIndex.getOrderEntries(fileOrDir));
  }

  @Override
  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, false, false, ModuleSourceRootData.class);
    return fileSet != null && isFromThisModule(fileSet) && fileSet.getKind() == WorkspaceFileKind.TEST_CONTENT;
  }

  @Override
  public boolean isUnderSourceRootOfType(@NotNull VirtualFile fileOrDir, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, false, false, ModuleSourceRootData.class);
    return isFromThisModule(fileSet) && ProjectFileIndexImpl.isSourceRootOfType(fileSet, rootTypes);
  }

  @Override
  protected boolean isScopeDisposed() {
    return myModule.isDisposed();
  }

  public static @Nullable OrderEntry findOrderEntryWithOwnerModule(@NotNull Module ownerModule, @NotNull List<? extends OrderEntry> orderEntries) {
    if (orderEntries.size() < 10) {
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry.getOwnerModule() == ownerModule) {
          return orderEntry;
        }
      }
      return null;
    }
    int index = Collections.binarySearch(orderEntries, new FakeOrderEntry(ownerModule), RootIndex.BY_OWNER_MODULE);
    return index < 0 ? null : orderEntries.get(index);
  }

  private static @NotNull List<OrderEntry> findAllOrderEntriesWithOwnerModule(@NotNull Module ownerModule, @NotNull List<? extends OrderEntry> entries) {
    if (entries.isEmpty()) return Collections.emptyList();

    if (entries.size() == 1) {
      OrderEntry entry = entries.get(0);
      return entry.getOwnerModule() == ownerModule ?
             new ArrayList<>(entries) : Collections.emptyList();
    }
    int index = Collections.binarySearch(entries, new FakeOrderEntry(ownerModule), RootIndex.BY_OWNER_MODULE);
    if (index < 0) {
      return Collections.emptyList();
    }
    int firstIndex = index;
    while (firstIndex - 1 >= 0 && entries.get(firstIndex - 1).getOwnerModule() == ownerModule) {
      firstIndex--;
    }
    int lastIndex = index + 1;
    while (lastIndex < entries.size() && entries.get(lastIndex).getOwnerModule() == ownerModule) {
      lastIndex++;
    }
    return new ArrayList<>(entries.subList(firstIndex, lastIndex));
  }

  private static class FakeOrderEntry implements OrderEntry {
    private final Module myOwnerModule;

    FakeOrderEntry(@NotNull Module ownerModule) {
      myOwnerModule = ownerModule;
    }

    @Override
    public VirtualFile @NotNull [] getFiles(@NotNull OrderRootType type) {
      throw new IncorrectOperationException();
    }

    @Override
    public @NotNull String getPresentableName() {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean isValid() {
      throw new IncorrectOperationException();
    }

    @Override
    public @NotNull Module getOwnerModule() {
      return myOwnerModule;
    }

    @Override
    public <R> R accept(@NotNull RootPolicy<R> policy, @Nullable R initialValue) {
      throw new IncorrectOperationException();
    }

    @Override
    public int compareTo(@NotNull OrderEntry o) {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean isSynthetic() {
      throw new IncorrectOperationException();
    }
  }

  @Override
  protected boolean isInContent(@NotNull WorkspaceFileSetWithCustomData<?> fileSet) {
    return fileSet.getData() instanceof ModuleRelatedRootData data && myModule.equals(data.getModule());
  }
}
