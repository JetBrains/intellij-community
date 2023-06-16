// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData;
import com.intellij.workspaceModel.core.fileIndex.impl.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;
import java.util.function.Function;

/**
 * This is an internal class, {@link ModuleFileIndex} must be used instead.
 */
@ApiStatus.Internal
public class ModuleFileIndexImpl extends FileIndexBase implements ModuleFileIndex {
  @NotNull
  private final Module myModule;

  public ModuleFileIndexImpl(@NotNull Module module) {
    super(module.getProject());

    myModule = module;
  }

  @Override
  public boolean iterateContent(@NotNull ContentIterator processor, @Nullable VirtualFileFilter filter) {
    Collection<VirtualFile> roots = ReadAction.nonBlocking(() -> {
      Project project = myModule.getProject();
      if (project.isDisposed()) return null;
      WorkspaceFileIndexEx index = (WorkspaceFileIndexEx)WorkspaceFileIndex.getInstance(project);
      Collection<VirtualFile> files = new HashSet<>();
      index.visitFileSets(new WorkspaceFileSetVisitor() {
        @Override
        public void visitIncludedRoot(@NotNull WorkspaceFileSet fileSet) {
          if (fileSet instanceof WorkspaceFileSetWithCustomData<?> && isInContent((WorkspaceFileSetWithCustomData<?>)fileSet)) {
            files.add(fileSet.getRoot());
          }
        }
      });
      return files;
    }).executeSynchronously();
    Collection<VirtualFile> highestRoots = selectRootItems(roots, root -> root.getPath());

    for (VirtualFile contentRoot : highestRoots) {
      if (!iterateContentUnderDirectory(contentRoot, processor, filter)) {
        return false;
      }
    }
    return true;
  }

  private static <T> Collection<T> selectRootItems(Collection<T> items, Function<T, String> toPath) {//todo[lene] remove copy-paste
    if (items.size() < 2) {
      return items;
    }

    TreeMap<String, T> pathMap = new TreeMap<>(OSAgnosticPathUtil.COMPARATOR);
    for (T item : items) {
      String path = FileUtil.toSystemIndependentName(toPath.apply(item));
      if (!isIncluded(pathMap, path)) {
        pathMap.put(path, item);
        while (true) {
          String excludedPath = pathMap.higherKey(path);
          if (excludedPath != null && OSAgnosticPathUtil.startsWith(excludedPath, path)) {
            pathMap.remove(excludedPath);
          }
          else {
            break;
          }
        }
      }
    }
    return pathMap.values();
  }

  private static boolean isIncluded(NavigableMap<String, ?> existingFiles, String path) {
    String suggestedCoveringRoot = existingFiles.floorKey(path);
    return suggestedCoveringRoot != null && OSAgnosticPathUtil.startsWith(path, suggestedCoveringRoot);
  }

  @Override
  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSetWithCustomData<ModuleContentOrSourceRootData> fileSet = 
      myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, false, ModuleContentOrSourceRootData.class);
    return isFromThisModule(fileSet);
  }

  private boolean isFromThisModule(@Nullable WorkspaceFileSetWithCustomData<? extends ModuleRelatedRootData> fileSet) {
    return fileSet != null && fileSet.getData().getModule().equals(myModule);
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, false, ModuleSourceRootData.class);
    return fileSet != null && isFromThisModule(fileSet);
  }

  @Override
  @NotNull
  public List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile fileOrDir) {
    return findAllOrderEntriesWithOwnerModule(myModule, myDirectoryIndex.getOrderEntries(fileOrDir));
  }

  @Override
  public OrderEntry getOrderEntryForFile(@NotNull VirtualFile fileOrDir) {
    return findOrderEntryWithOwnerModule(myModule, myDirectoryIndex.getOrderEntries(fileOrDir));
  }

  @Override
  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, false, ModuleSourceRootData.class);
    return fileSet != null && isFromThisModule(fileSet) && fileSet.getKind() == WorkspaceFileKind.TEST_CONTENT;
  }

  @Override
  public boolean isUnderSourceRootOfType(@NotNull VirtualFile fileOrDir, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    WorkspaceFileSetWithCustomData<ModuleSourceRootData> fileSet = myWorkspaceFileIndex.findFileSetWithCustomData(fileOrDir, true, true, false, false, ModuleSourceRootData.class);
    return isFromThisModule(fileSet) && ProjectFileIndexImpl.isSourceRootOfType(fileSet, rootTypes);
  }

  @Override
  protected boolean isScopeDisposed() {
    return myModule.isDisposed();
  }

  @Nullable
  public static OrderEntry findOrderEntryWithOwnerModule(@NotNull Module ownerModule, @NotNull List<? extends OrderEntry> orderEntries) {
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

  @NotNull
  private static List<OrderEntry> findAllOrderEntriesWithOwnerModule(@NotNull Module ownerModule, @NotNull List<? extends OrderEntry> entries) {
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
    public String @NotNull [] getUrls(@NotNull OrderRootType rootType) {
      throw new IncorrectOperationException();
    }

    @NotNull
    @Override
    public String getPresentableName() {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean isValid() {
      throw new IncorrectOperationException();
    }

    @NotNull
    @Override
    public Module getOwnerModule() {
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
    return fileSet.getData() instanceof ModuleContentOrSourceRootData data && myModule.equals(data.getModule());
  }
}
