/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

/**
 * @author nik
 */
public abstract class RootModelBase implements ModuleRootModel {
  @Override
  @NotNull
  public VirtualFile[] getContentRoots() {
    final ArrayList<VirtualFile> result = new ArrayList<>();

    for (ContentEntry contentEntry : getContent()) {
      final VirtualFile file = contentEntry.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  @NotNull
  public String[] getContentRootUrls() {
    if (getContent().isEmpty()) return ArrayUtil.EMPTY_STRING_ARRAY;
    final ArrayList<String> result = new ArrayList<>(getContent().size());

    for (ContentEntry contentEntry : getContent()) {
      result.add(contentEntry.getUrl());
    }

    return ArrayUtil.toStringArray(result);
  }

  @Override
  @NotNull
  public String[] getExcludeRootUrls() {
    final List<String> result = new SmartList<>();
    for (ContentEntry contentEntry : getContent()) {
      result.addAll(contentEntry.getExcludeFolderUrls());
    }
    return ArrayUtil.toStringArray(result);
  }

  @Override
  @NotNull
  public VirtualFile[] getExcludeRoots() {
    final List<VirtualFile> result = new SmartList<>();
    for (ContentEntry contentEntry : getContent()) {
      Collections.addAll(result, contentEntry.getExcludeFolderFiles());
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  @NotNull
  public String[] getSourceRootUrls() {
    return getSourceRootUrls(true);
  }

  @Override
  @NotNull
  public String[] getSourceRootUrls(boolean includingTests) {
    List<String> result = new SmartList<>();
    for (ContentEntry contentEntry : getContent()) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        if (includingTests || !sourceFolder.isTestSource()) {
          result.add(sourceFolder.getUrl());
        }
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  @Override
  @NotNull
  public VirtualFile[] getSourceRoots() {
    return getSourceRoots(true);
  }

  @Override
  @NotNull
  public VirtualFile[] getSourceRoots(final boolean includingTests) {
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

  @NotNull
  @Override
  public ContentEntry[] getContentEntries() {
    final Collection<ContentEntry> content = getContent();
    return content.toArray(new ContentEntry[content.size()]);
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
  public <R> R processOrder(RootPolicy<R> policy, R initialValue) {
    R result = initialValue;
    for (OrderEntry orderEntry : getOrderEntries()) {
      result = orderEntry.accept(policy, result);
    }
    return result;
  }

  @Override
  @NotNull
  public String[] getDependencyModuleNames() {
    List<String> result = orderEntries().withoutSdk().withoutLibraries().withoutModuleSourceEntries()
      .process(new CollectDependentModules(), new ArrayList<>());
    return ArrayUtil.toStringArray(result);
  }

  @Override
  @NotNull
  public Module[] getModuleDependencies() {
    return getModuleDependencies(true);
  }

  @Override
  @NotNull
  public Module[] getModuleDependencies(boolean includeTests) {
    OrderEntry[] entries = getOrderEntries();
    List<Module> result = new ArrayList<>(entries.length);

    for (OrderEntry entry : entries) {
      if (entry instanceof ModuleOrderEntry) {
        DependencyScope scope = ((ModuleOrderEntry)entry).getScope();
        if (includeTests || scope.isForProductionCompile() || scope.isForProductionRuntime()) {
          Module module = ((ModuleOrderEntry)entry).getModule();
          if (module != null) {
            result.add(module);
          }
        }
      }
    }

    return result.isEmpty() ? Module.EMPTY_ARRAY : ContainerUtil.toArray(result, new Module[result.size()]);
  }

  private static class CollectDependentModules extends RootPolicy<List<String>> {
    @NotNull
    @Override
    public List<String> visitModuleOrderEntry(@NotNull ModuleOrderEntry moduleOrderEntry, @NotNull List<String> arrayList) {
      arrayList.add(moduleOrderEntry.getModuleName());
      return arrayList;
    }
  }
}
