// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

public class ModuleFileIndexImpl extends FileIndexBase implements ModuleFileIndex {
  @NotNull
  private final Module myModule;

  public ModuleFileIndexImpl(@NotNull Module module, @NotNull DirectoryIndex directoryIndex) {
    super(directoryIndex, FileTypeRegistry.getInstance());
    myModule = module;
  }

  @Override
  public boolean iterateContent(@NotNull ContentIterator processor, @Nullable VirtualFileFilter filter) {
    Set<VirtualFile> contentRoots = getModuleRootsToIterate();
    for (VirtualFile contentRoot : contentRoots) {
      if (!iterateContentUnderDirectory(contentRoot, processor, filter)) {
        return false;
      }
    }
    return true;
  }


  @NotNull
  Set<VirtualFile> getModuleRootsToIterate() {
    return ReadAction.compute(() -> {
      if (myModule.isDisposed()) return Collections.emptySet();
      Set<VirtualFile> result = new LinkedHashSet<>();
      VirtualFile[][] allRoots = getModuleContentAndSourceRoots(myModule);
      for (VirtualFile[] roots : allRoots) {
        for (VirtualFile root : roots) {
          DirectoryInfo info = getInfoForFileOrDirectory(root);
          if (!info.isInProject(root)) continue;

          VirtualFile parent = root.getParent();
          if (parent != null) {
            DirectoryInfo parentInfo = myDirectoryIndex.getInfoForFile(parent);
            if (myModule.equals(parentInfo.getModule())) continue; // inner content - skip it
          }
          result.add(root);
        }
      }
      return result;
    });
  }

  @Override
  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    return isInContent(fileOrDir, getInfoForFileOrDirectory(fileOrDir));
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource(fileOrDir) && myModule.equals(info.getModule());
  }

  @Override
  @NotNull
  public List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile fileOrDir) {
    return findAllOrderEntriesWithOwnerModule(myModule, myDirectoryIndex.getOrderEntries(getInfoForFileOrDirectory(fileOrDir)));
  }

  @Override
  public OrderEntry getOrderEntryForFile(@NotNull VirtualFile fileOrDir) {
    return findOrderEntryWithOwnerModule(myModule, myDirectoryIndex.getOrderEntries(getInfoForFileOrDirectory(fileOrDir)));
  }

  @Override
  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource(fileOrDir) && myModule.equals(info.getModule()) && isTestSourcesRoot(info);
  }

  @Override
  public boolean isUnderSourceRootOfType(@NotNull VirtualFile fileOrDir, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource(fileOrDir) && myModule.equals(info.getModule()) && rootTypes.contains(myDirectoryIndex.getSourceRootType(info));
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

    @NotNull
    @Override
    public VirtualFile[] getFiles(@NotNull OrderRootType type) {
      throw new IncorrectOperationException();
    }

    @NotNull
    @Override
    public String[] getUrls(@NotNull OrderRootType rootType) {
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
  protected boolean isInContent(@NotNull VirtualFile file, @NotNull DirectoryInfo info) {
    return ProjectFileIndexImpl.isFileInContent(file, info) && myModule.equals(info.getModule());
  }
}
