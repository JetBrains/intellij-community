// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.util.JpsPathUtil;

import java.util.*;

class OrderRootsEnumeratorImpl implements OrderRootsEnumerator {
  private static final Logger LOG = Logger.getInstance(OrderRootsEnumeratorImpl.class);
  private final OrderEnumeratorBase myOrderEnumerator;
  private final OrderRootType myRootType;
  private final NotNullFunction<? super OrderEntry, ? extends OrderRootType> myRootTypeProvider;
  private boolean myUsingCache;
  private NotNullFunction<? super JdkOrderEntry, VirtualFile[]> myCustomSdkRootProvider;
  private boolean myWithoutSelfModuleOutput;

  OrderRootsEnumeratorImpl(@NotNull OrderEnumeratorBase orderEnumerator, @NotNull OrderRootType rootType) {
    myOrderEnumerator = orderEnumerator;
    myRootType = rootType;
    myRootTypeProvider = null;
  }

  OrderRootsEnumeratorImpl(@NotNull OrderEnumeratorBase orderEnumerator,
                           @NotNull NotNullFunction<? super OrderEntry, ? extends OrderRootType> rootTypeProvider) {
    myOrderEnumerator = orderEnumerator;
    myRootType = null;
    myRootTypeProvider = rootTypeProvider;
  }

  @Override
  public VirtualFile @NotNull [] getRoots() {
    if (myUsingCache) {
      checkCanUseCache();
      final OrderRootsCache cache = myOrderEnumerator.getCache();
      final int flags = myOrderEnumerator.getFlags();
      return cache.getOrComputeRoots(myRootType, flags, this::computeRootsUrls);
    }

    ClassicOrderRootComputer computer = new ClassicOrderRootComputer(myOrderEnumerator,
                                                                     this::getRootType,
                                                                     myCustomSdkRootProvider,
                                                                     myWithoutSelfModuleOutput);
    Collection<VirtualFile> roots = computer.computeRoots();
    return VfsUtilCore.toVirtualFileArray(roots);
  }

  @Override
  public @NotNull Collection<RootEntry> getRootEntries() {
    // todo IJPL-339 do we need to support cache like in {@link #getRoots}?

    MutliverseOrderRootComputer computer = new MutliverseOrderRootComputer(myOrderEnumerator,
                                                                           this::getRootType,
                                                                           myCustomSdkRootProvider,
                                                                           myWithoutSelfModuleOutput);
    Collection<RootEntry> entries = computer.computeRoots();
    return entries;
  }

  @Override
  public String @NotNull [] getUrls() {
    if (myUsingCache) {
      checkCanUseCache();
      final OrderRootsCache cache = myOrderEnumerator.getCache();
      final int flags = myOrderEnumerator.getFlags();
      return cache.getOrComputeUrls(myRootType, flags, this::computeRootsUrls);
    }
    return ArrayUtilRt.toStringArray(computeRootsUrls());
  }

  private void checkCanUseCache() {
    LOG.assertTrue(myRootTypeProvider == null, "Caching not supported for OrderRootsEnumerator with root type provider");
    LOG.assertTrue(myCustomSdkRootProvider == null, "Caching not supported for OrderRootsEnumerator with 'usingCustomSdkRootProvider' option");
    LOG.assertTrue(!myWithoutSelfModuleOutput, "Caching not supported for OrderRootsEnumerator with 'withoutSelfModuleOutput' option");
  }

  private @NotNull Collection<String> computeRootsUrls() {
    final Collection<String> result = new LinkedHashSet<>();
    myOrderEnumerator.forEach((orderEntry, customHandlers) -> {
      OrderRootType type = getRootType(orderEntry);

      if (orderEntry instanceof ModuleSourceOrderEntry) {
        ModuleRootModel rootModel = ((ModuleSourceOrderEntry)orderEntry).getRootModel();
        boolean includeTests = !myOrderEnumerator.isProductionOnly()
                               && (OrderEnumeratorBase.shouldIncludeTestsFromDependentModulesToTestClasspath(customHandlers) || myOrderEnumerator.isRootModuleModel(rootModel));
        collectModuleRootsUrls(type, rootModel, result, true, includeTests, customHandlers);
      }
      else if (orderEntry instanceof ModuleOrderEntry moduleOrderEntry) {
        final Module module = moduleOrderEntry.getModule();
        if (module != null) {
          ModuleRootModel rootModel = myOrderEnumerator.getRootModel(module);
          boolean productionOnTests = ((ModuleOrderEntry)orderEntry).isProductionOnTestDependency();
          boolean includeTests = !myOrderEnumerator.isProductionOnly() && OrderEnumeratorBase.shouldIncludeTestsFromDependentModulesToTestClasspath(customHandlers)
                                 || productionOnTests;
          collectModuleRootsUrls(type, rootModel, result, !productionOnTests, includeTests, customHandlers);
        }
      }
      else if (orderEntry instanceof LibraryOrSdkOrderEntry) {
        if (OrderEnumeratorBase.addCustomRootUrlsForLibraryOrSdk((LibraryOrSdkOrderEntry)orderEntry, type, result, customHandlers)) {
          return true;
        }
        Collections.addAll(result, ((LibraryOrSdkOrderEntry)orderEntry).getRootUrls(type));
      }
      else {
        LOG.error("Unexpected implementation of OrderEntry: " + orderEntry.getClass().getName());
      }
      return true;
    });
    return result;
  }

  @Override
  public @NotNull PathsList getPathsList() {
    final PathsList list = new PathsList();
    collectPaths(list);
    return list;
  }

  @Override
  public void collectPaths(@NotNull PathsList list) {
    list.addVirtualFiles(getRoots());
    //     list.addAll(Arrays.stream(getUrls()).map(url -> FileUtilRt.toSystemDependentName(JpsPathUtil.urlToPath(url))).toList());
  }

  @Override
  public @NotNull OrderRootsEnumerator usingCache() {
    myUsingCache = true;
    return this;
  }

  @Override
  public @NotNull OrderRootsEnumerator withoutSelfModuleOutput() {
    myWithoutSelfModuleOutput = true;
    return this;
  }

  @Override
  public @NotNull OrderRootsEnumerator usingCustomSdkRootProvider(@NotNull NotNullFunction<? super JdkOrderEntry, VirtualFile[]> provider) {
    myCustomSdkRootProvider = provider;
    return this;
  }

  private void collectModuleRootsUrls(@NotNull OrderRootType type,
                                      @NotNull ModuleRootModel rootModel,
                                      @NotNull Collection<String> result,
                                      boolean includeProduction, boolean includeTests,
                                      @NotNull List<? extends OrderEnumerationHandler> customHandlers) {
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
      boolean hasTests = false;
      boolean hasProduction = false;
      for (ContentEntry contentEntry : rootModel.getContentEntries()) {
        for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          if (sourceFolder.isTestSource()) {
            hasTests = true;
          }
          else {
            hasProduction = true;
          }
        }
      }
      includeTests &= hasTests;
      includeProduction &= hasProduction;
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
          else if (includeTests) {
            ContainerUtil.addIfNotNull(result, extension.getCompilerOutputUrlForTests());
          }
        }
      }
    }
    OrderEnumeratorBase.addCustomRootsUrlsForModule(type, rootModel, result, includeProduction, includeTests, customHandlers);
  }

  private @NotNull OrderRootType getRootType(@NotNull OrderEntry e) {
    return myRootType != null ? myRootType : myRootTypeProvider.fun(e);
  }
}
