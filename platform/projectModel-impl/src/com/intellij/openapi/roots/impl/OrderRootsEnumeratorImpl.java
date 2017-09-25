/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author nik
 */
class OrderRootsEnumeratorImpl implements OrderRootsEnumerator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.OrderRootsEnumeratorImpl");
  private final OrderEnumeratorBase myOrderEnumerator;
  private final OrderRootType myRootType;
  private final NotNullFunction<OrderEntry, OrderRootType> myRootTypeProvider;
  private boolean myUsingCache;
  private NotNullFunction<OrderEntry, VirtualFile[]> myCustomRootProvider;
  private boolean myWithoutSelfModuleOutput;

  OrderRootsEnumeratorImpl(@NotNull OrderEnumeratorBase orderEnumerator, @NotNull OrderRootType rootType) {
    myOrderEnumerator = orderEnumerator;
    myRootType = rootType;
    myRootTypeProvider = null;
  }

  OrderRootsEnumeratorImpl(@NotNull OrderEnumeratorBase orderEnumerator,
                           @NotNull NotNullFunction<OrderEntry, OrderRootType> rootTypeProvider) {
    myOrderEnumerator = orderEnumerator;
    myRootTypeProvider = rootTypeProvider;
    myRootType = null;
  }

  @NotNull
  @Override
  public VirtualFile[] getRoots() {
    if (myUsingCache) {
      checkCanUseCache();
      final OrderRootsCache cache = myOrderEnumerator.getCache();
      if (cache != null) {
        final int flags = myOrderEnumerator.getFlags();
        final VirtualFile[] cached = cache.getCachedRoots(myRootType, flags);
        if (cached == null) {
          return cache.setCachedRoots(myRootType, flags, computeRootsUrls()).getFiles();
        }
        return cached;
      }
    }

    return VfsUtilCore.toVirtualFileArray(computeRoots());
  }

  @NotNull
  @Override
  public String[] getUrls() {
    if (myUsingCache) {
      checkCanUseCache();
      final OrderRootsCache cache = myOrderEnumerator.getCache();
      if (cache != null) {
        final int flags = myOrderEnumerator.getFlags();
        String[] cached = cache.getCachedUrls(myRootType, flags);
        if (cached == null) {
          return cache.setCachedRoots(myRootType, flags, computeRootsUrls()).getUrls();
        }
        return cached;
      }
    }
    return ArrayUtil.toStringArray(computeRootsUrls());
  }

  private void checkCanUseCache() {
    LOG.assertTrue(myRootTypeProvider == null, "Caching not supported for OrderRootsEnumerator with root type provider");
    LOG.assertTrue(myCustomRootProvider == null, "Caching not supported for OrderRootsEnumerator with 'usingCustomRootProvider' option");
    LOG.assertTrue(!myWithoutSelfModuleOutput, "Caching not supported for OrderRootsEnumerator with 'withoutSelfModuleOutput' option");
  }

  private Collection<VirtualFile> computeRoots() {
    final Collection<VirtualFile> result = new LinkedHashSet<>();
    myOrderEnumerator.forEach((orderEntry, customHandlers) -> {
      OrderRootType type = getRootType(orderEntry);

      if (orderEntry instanceof ModuleSourceOrderEntry) {
        collectModuleRoots(type, ((ModuleSourceOrderEntry)orderEntry).getRootModel(), result, true, !myOrderEnumerator.isProductionOnly(),
                           customHandlers);
      }
      else if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
        final Module module = moduleOrderEntry.getModule();
        if (module != null) {
          ModuleRootModel rootModel = myOrderEnumerator.getRootModel(module);
          boolean productionOnTests = orderEntry instanceof ModuleOrderEntryImpl
                                      && ((ModuleOrderEntryImpl)orderEntry).isProductionOnTestDependency();
          boolean includeTests = !myOrderEnumerator.isProductionOnly()
                                 && OrderEnumeratorBase.shouldIncludeTestsFromDependentModulesToTestClasspath(customHandlers)
                                 || productionOnTests;
          collectModuleRoots(type, rootModel, result, !productionOnTests, includeTests, customHandlers);
        }
      }
      else {
        if (myCustomRootProvider != null) {
          Collections.addAll(result, myCustomRootProvider.fun(orderEntry));
          return true;
        }
        if (OrderEnumeratorBase.addCustomRootsForLibrary(orderEntry, type, result, customHandlers)) {
          return true;
        }
        Collections.addAll(result, orderEntry.getFiles(type));
      }
      return true;
    });
    return result;
  }

  @NotNull
  private Collection<String> computeRootsUrls() {
    final Collection<String> result = new LinkedHashSet<>();
    myOrderEnumerator.forEach((orderEntry, customHandlers) -> {
      OrderRootType type = getRootType(orderEntry);

      if (orderEntry instanceof ModuleSourceOrderEntry) {
        collectModuleRootsUrls(type, ((ModuleSourceOrderEntry)orderEntry).getRootModel(), result, true, !myOrderEnumerator.isProductionOnly());
      }
      else if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
        final Module module = moduleOrderEntry.getModule();
        if (module != null) {
          ModuleRootModel rootModel = myOrderEnumerator.getRootModel(module);
          boolean productionOnTests = orderEntry instanceof ModuleOrderEntryImpl
                                      && ((ModuleOrderEntryImpl)orderEntry).isProductionOnTestDependency();
          boolean includeTests = !myOrderEnumerator.isProductionOnly() && OrderEnumeratorBase
            .shouldIncludeTestsFromDependentModulesToTestClasspath(customHandlers)
                                 || productionOnTests;
          collectModuleRootsUrls(type, rootModel, result, !productionOnTests, includeTests);
        }
      }
      else {
        if (OrderEnumeratorBase.addCustomRootUrlsForLibrary(orderEntry, type, result, customHandlers)) {
          return true;
        }
        Collections.addAll(result, orderEntry.getUrls(type));
      }
      return true;
    });
    return result;
  }

  @NotNull
  @Override
  public PathsList getPathsList() {
    final PathsList list = new PathsList();
    collectPaths(list);
    return list;
  }

  @Override
  public void collectPaths(@NotNull PathsList list) {
    list.addVirtualFiles(getRoots());
  }

  @NotNull
  @Override
  public OrderRootsEnumerator usingCache() {
    myUsingCache = true;
    return this;
  }

  @NotNull
  @Override
  public OrderRootsEnumerator withoutSelfModuleOutput() {
    myWithoutSelfModuleOutput = true;
    return this;
  }

  @NotNull
  @Override
  public OrderRootsEnumerator usingCustomRootProvider(@NotNull NotNullFunction<OrderEntry, VirtualFile[]> provider) {
    myCustomRootProvider = provider;
    return this;
  }

  private void collectModuleRoots(@NotNull OrderRootType type,
                                  ModuleRootModel rootModel,
                                  @NotNull Collection<VirtualFile> result,
                                  final boolean includeProduction,
                                  final boolean includeTests,
                                  @NotNull List<OrderEnumerationHandler> customHandlers) {
    if (type.equals(OrderRootType.SOURCES)) {
      if (includeProduction) {
        Collections.addAll(result, rootModel.getSourceRoots(includeTests));
      }
      else {
        result.addAll(rootModel.getSourceRoots(JavaModuleSourceRootTypes.TESTS));
      }
    }
    else if (type.equals(OrderRootType.CLASSES)) {
      final CompilerModuleExtension extension = rootModel.getModuleExtension(CompilerModuleExtension.class);
      if (extension != null) {
        if (myWithoutSelfModuleOutput && myOrderEnumerator.isRootModuleModel(rootModel)) {
          if (includeTests && includeProduction) {
            Collections.addAll(result, extension.getOutputRoots(false));
          }
        }
        else {
          if (includeProduction) {
            Collections.addAll(result, extension.getOutputRoots(includeTests));
          }
          else {
            ContainerUtil.addIfNotNull(result, extension.getCompilerOutputPathForTests());
          }
        }
      }
    }
    OrderEnumeratorBase.addCustomRootsForModule(type, rootModel, result, includeProduction, includeTests, customHandlers);
  }

  private void collectModuleRootsUrls(OrderRootType type,
                                      ModuleRootModel rootModel,
                                      Collection<String> result, final boolean includeProduction, final boolean includeTests) {
    if (type.equals(OrderRootType.SOURCES)) {
      if (includeProduction) {
        Collections.addAll(result, rootModel.getSourceRootUrls(includeTests));
      }
      else {
        for (ContentEntry entry : rootModel.getContentEntries()) {
          for (SourceFolder folder : entry.getSourceFolders(JavaModuleSourceRootTypes.TESTS)) {
            result.add(folder.getUrl());
          }
        }
      }
    }
    else if (type.equals(OrderRootType.CLASSES)) {
      final CompilerModuleExtension extension = rootModel.getModuleExtension(CompilerModuleExtension.class);
      if (extension != null) {
        if (myWithoutSelfModuleOutput && myOrderEnumerator.isRootModuleModel(rootModel)) {
          if (includeTests && includeProduction) {
            Collections.addAll(result, extension.getOutputRootUrls(false));
          }
        }
        else {
          if (includeProduction) {
            Collections.addAll(result, extension.getOutputRootUrls(includeTests));
          }
          else {
            ContainerUtil.addIfNotNull(result, extension.getCompilerOutputUrlForTests());
          }
        }
      }
    }
  }

  @NotNull
  private OrderRootType getRootType(@NotNull OrderEntry e) {
    return myRootType != null ? myRootType : myRootTypeProvider.fun(e);
  }
}
