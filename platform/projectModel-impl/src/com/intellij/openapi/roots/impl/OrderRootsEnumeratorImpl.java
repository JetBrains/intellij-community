// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

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

    return VfsUtilCore.toVirtualFileArray(computeRoots());
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

  @NotNull
  private Collection<VirtualFile> computeRoots() {
    final Collection<VirtualFile> result = new LinkedHashSet<>();
    myOrderEnumerator.forEach((orderEntry, customHandlers) -> {
      OrderRootType type = getRootType(orderEntry);

      if (orderEntry instanceof ModuleSourceOrderEntry) {
        collectModuleRoots(type, ((ModuleSourceOrderEntry)orderEntry).getRootModel(), result, true, !myOrderEnumerator.isProductionOnly(),
                           customHandlers);
      }
      else if (orderEntry instanceof ModuleOrderEntry moduleOrderEntry) {
        final Module module = moduleOrderEntry.getModule();
        if (module != null) {
          ModuleRootModel rootModel = myOrderEnumerator.getRootModel(module);
          boolean productionOnTests = ((ModuleOrderEntry)orderEntry).isProductionOnTestDependency();
          boolean includeTests = !myOrderEnumerator.isProductionOnly()
                                 && OrderEnumeratorBase.shouldIncludeTestsFromDependentModulesToTestClasspath(customHandlers)
                                 || productionOnTests;
          collectModuleRoots(type, rootModel, result, !productionOnTests, includeTests, customHandlers);
        }
      }
      else if (orderEntry instanceof LibraryOrSdkOrderEntry) {
        if (myCustomSdkRootProvider != null && orderEntry instanceof JdkOrderEntry) {
          Collections.addAll(result, myCustomSdkRootProvider.fun((JdkOrderEntry)orderEntry));
          return true;
        }
        if (addCustomRootsForLibraryOrSdk((LibraryOrSdkOrderEntry)orderEntry, type, result, customHandlers)) {
          return true;
        }
        Collections.addAll(result, ((LibraryOrSdkOrderEntry)orderEntry).getRootFiles(type));
      }
      else {
        LOG.error("Unexpected implementation of OrderEntry: " + orderEntry.getClass().getName());
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
        boolean includeTests = !myOrderEnumerator.isProductionOnly();
        collectModuleRootsUrls(type, ((ModuleSourceOrderEntry)orderEntry).getRootModel(), result, true, includeTests, customHandlers);
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

  @Override
  public @NotNull OrderRootsEnumerator usingCustomSdkRootProvider(@NotNull NotNullFunction<? super JdkOrderEntry, VirtualFile[]> provider) {
    myCustomSdkRootProvider = provider;
    return this;
  }

  private void collectModuleRoots(@NotNull OrderRootType type,
                                  ModuleRootModel rootModel,
                                  @NotNull Collection<? super VirtualFile> result,
                                  final boolean includeProduction,
                                  final boolean includeTests,
                                  @NotNull List<? extends OrderEnumerationHandler> customHandlers) {
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
    addCustomRootsForModule(type, rootModel, result, includeProduction, includeTests, customHandlers);
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

  @NotNull
  private OrderRootType getRootType(@NotNull OrderEntry e) {
    return myRootType != null ? myRootType : myRootTypeProvider.fun(e);
  }

  private static boolean addCustomRootsForLibraryOrSdk(@NotNull LibraryOrSdkOrderEntry forOrderEntry,
                                                       @NotNull OrderRootType type,
                                                       @NotNull Collection<? super VirtualFile> result,
                                                       @NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      final List<String> urls = new ArrayList<>();
      final boolean added =
        handler.addCustomRootsForLibraryOrSdk(forOrderEntry, type, urls);
      for (String url : urls) {
        ContainerUtil.addIfNotNull(result, VirtualFileManager.getInstance().findFileByUrl(url));
      }
      if (added) {
        return true;
      }
    }
    return false;
  }

  private static boolean addCustomRootsForModule(@NotNull OrderRootType type,
                                                 @NotNull ModuleRootModel rootModel,
                                                 @NotNull Collection<? super VirtualFile> result,
                                                 boolean includeProduction,
                                                 boolean includeTests,
                                                 @NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      final List<String> urls = new ArrayList<>();
      final boolean added = handler.addCustomModuleRoots(type, rootModel, urls, includeProduction, includeTests);
      for (String url : urls) {
        ContainerUtil.addIfNotNull(result, VirtualFileManager.getInstance().findFileByUrl(url));
      }

      if (added) return true;
    }
    return false;
  }
}
