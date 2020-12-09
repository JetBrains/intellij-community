// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

@ApiStatus.Internal
public abstract class RootModelBase implements ModuleRootModel {
  @Override
  public VirtualFile @NotNull [] getContentRoots() {
    Collection<ContentEntry> content = getContent();
    List<VirtualFile> result = new ArrayList<>(content.size());
    for (ContentEntry contentEntry : content) {
      final VirtualFile file = contentEntry.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public String @NotNull [] getContentRootUrls() {
    Collection<ContentEntry> content = getContent();
    if (content.isEmpty()) return ArrayUtilRt.EMPTY_STRING_ARRAY;
    List<String> result = new ArrayList<>(content.size());

    for (ContentEntry contentEntry : content) {
      result.add(contentEntry.getUrl());
    }

    return ArrayUtilRt.toStringArray(result);
  }

  @Override
  public String @NotNull [] getExcludeRootUrls() {
    final List<String> result = new SmartList<>();
    for (ContentEntry contentEntry : getContent()) {
      result.addAll(contentEntry.getExcludeFolderUrls());
    }
    return ArrayUtilRt.toStringArray(result);
  }

  @Override
  public VirtualFile @NotNull [] getExcludeRoots() {
    final List<VirtualFile> result = new SmartList<>();
    for (ContentEntry contentEntry : getContent()) {
      Collections.addAll(result, contentEntry.getExcludeFolderFiles());
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public String @NotNull [] getSourceRootUrls() {
    return getSourceRootUrls(true);
  }

  @Override
  public String @NotNull [] getSourceRootUrls(boolean includingTests) {
    List<String> result = new SmartList<>();
    for (ContentEntry contentEntry : getContent()) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        if (includingTests || !sourceFolder.isTestSource()) {
          result.add(sourceFolder.getUrl());
        }
      }
    }
    return ArrayUtilRt.toStringArray(result);
  }

  @Override
  public VirtualFile @NotNull [] getSourceRoots() {
    return getSourceRoots(true);
  }

  @Override
  public VirtualFile @NotNull [] getSourceRoots(final boolean includingTests) {
    List<VirtualFile> result = new SmartList<>();
    for (ContentEntry contentEntry : getContent()) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        final VirtualFile file = sourceFolder.getFile();
        if (file != null && (includingTests || !sourceFolder.isTestSource())) {
          result.add(file);
        }
      }
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public List<VirtualFile> getSourceRoots(@NotNull JpsModuleSourceRootType<?> rootType) {
    return getSourceRoots(Collections.singleton(rootType));
  }

  @NotNull
  @Override
  public List<VirtualFile> getSourceRoots(@NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    List<VirtualFile> result = new SmartList<>();
    for (ContentEntry contentEntry : getContent()) {
      final List<SourceFolder> sourceFolders = contentEntry.getSourceFolders(rootTypes);
      for (SourceFolder sourceFolder : sourceFolders) {
        final VirtualFile file = sourceFolder.getFile();
        if (file != null) {
          result.add(file);
        }
      }
    }
    return result;
  }

  @Override
  public ContentEntry @NotNull [] getContentEntries() {
    final Collection<ContentEntry> content = getContent();
    return content.toArray(new ContentEntry[0]);
  }

  protected abstract Collection<ContentEntry> getContent();

  @Override
  public Sdk getSdk() {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof JdkOrderEntry) {
        return ((JdkOrderEntry)orderEntry).getJdk();
      }
    }
    return null;
  }

  @Override
  public boolean isSdkInherited() {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof InheritedJdkOrderEntry) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries() {
    return new ModuleOrderEnumerator(this, null);
  }

  @Override
  public <R> R processOrder(@NotNull RootPolicy<R> policy, R initialValue) {
    R result = initialValue;
    for (OrderEntry orderEntry : getOrderEntries()) {
      result = orderEntry.accept(policy, result);
    }
    return result;
  }

  @Override
  public String @NotNull [] getDependencyModuleNames() {
    List<String> result = orderEntries().withoutSdk().withoutLibraries().withoutModuleSourceEntries()
      .process(new CollectDependentModules(), new ArrayList<>());
    return ArrayUtilRt.toStringArray(result);
  }

  @Override
  public Module @NotNull [] getModuleDependencies() {
    return getModuleDependencies(true);
  }

  @Override
  public Module @NotNull [] getModuleDependencies(boolean includeTests) {
    OrderEntry[] entries = getOrderEntries();
    List<Module> result = null;

    for (OrderEntry entry : entries) {
      if (entry instanceof ModuleOrderEntry) {
        DependencyScope scope = ((ModuleOrderEntry)entry).getScope();
        if (includeTests || scope.isForProductionCompile() || scope.isForProductionRuntime()) {
          Module module = ((ModuleOrderEntry)entry).getModule();
          if (module != null) {
            if (result == null) {
              result = new SmartList<>();
            }
            result.add(module);
          }
        }
      }
    }

    return result == null ? Module.EMPTY_ARRAY : result.toArray(Module.EMPTY_ARRAY);
  }

  public static class CollectDependentModules extends RootPolicy<List<String>> {
    @NotNull
    @Override
    public List<String> visitModuleOrderEntry(@NotNull ModuleOrderEntry moduleOrderEntry, @NotNull List<String> arrayList) {
      arrayList.add(moduleOrderEntry.getModuleName());
      return arrayList;
    }
  }
}
