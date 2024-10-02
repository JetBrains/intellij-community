// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.*;
import java.util.function.Function;

class OrderRootComputer {
  private static final Logger LOG = Logger.getInstance(OrderRootComputer.class);

  private final @NotNull OrderEnumeratorBase myOrderEnumerator;
  private final @NotNull Function<OrderEntry, OrderRootType> myOrderEntryToRootType;
  private final @Nullable NotNullFunction<? super JdkOrderEntry, VirtualFile[]> myCustomSdkRootProvider;
  private final boolean myWithoutSelfModuleOutput;

  OrderRootComputer(@NotNull OrderEnumeratorBase orderEnumerator,
                    @NotNull Function<OrderEntry, OrderRootType> orderEntryToRootType,
                    @Nullable NotNullFunction<? super JdkOrderEntry, VirtualFile[]> customSdkRootProvider,
                    boolean withoutSelfModuleOutput) {
    myOrderEnumerator = orderEnumerator;
    myOrderEntryToRootType = orderEntryToRootType;
    myCustomSdkRootProvider = customSdkRootProvider;
    myWithoutSelfModuleOutput = withoutSelfModuleOutput;
  }

  @NotNull Collection<VirtualFile> computeRoots() {
    Collection<VirtualFile> result = new LinkedHashSet<>();
    myOrderEnumerator.forEach((orderEntry, customHandlers) -> {
      OrderRootType type = myOrderEntryToRootType.apply(orderEntry);

      if (orderEntry instanceof ModuleSourceOrderEntry) {
        ModuleRootModel rootModel = ((ModuleSourceOrderEntry)orderEntry).getRootModel();
        boolean includeTests = !myOrderEnumerator.isProductionOnly() &&
                               (OrderEnumeratorBase.shouldIncludeTestsFromDependentModulesToTestClasspath(customHandlers) || myOrderEnumerator.isRootModuleModel(rootModel));
        collectModuleRoots(type, rootModel, result, true, includeTests, customHandlers);
      }
      else if (orderEntry instanceof ModuleOrderEntry moduleOrderEntry) {
        Module module = moduleOrderEntry.getModule();
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
    return result;  }

  private void collectModuleRoots(@NotNull OrderRootType type,
                                  ModuleRootModel rootModel,
                                  @NotNull Collection<? super VirtualFile> result,
                                  boolean includeProduction,
                                  boolean includeTests,
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
      CompilerModuleExtension extension = rootModel.getModuleExtension(CompilerModuleExtension.class);
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

  private static boolean addCustomRootsForLibraryOrSdk(@NotNull LibraryOrSdkOrderEntry forOrderEntry,
                                                       @NotNull OrderRootType type,
                                                       @NotNull Collection<? super VirtualFile> result,
                                                       @NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      List<String> urls = new ArrayList<>();
      boolean added = handler.addCustomRootsForLibraryOrSdk(forOrderEntry, type, urls);
      for (String url : urls) {
        ContainerUtil.addIfNotNull(result, VirtualFileManager.getInstance().findFileByUrl(url));
      }
      if (added) {
        return true;
      }
    }
    return false;
  }

  private static void addCustomRootsForModule(@NotNull OrderRootType type,
                                              @NotNull ModuleRootModel rootModel,
                                              @NotNull Collection<? super VirtualFile> result,
                                              boolean includeProduction,
                                              boolean includeTests,
                                              @NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      List<String> urls = new ArrayList<>();
      boolean added = handler.addCustomModuleRoots(type, rootModel, urls, includeProduction, includeTests);
      for (String url : urls) {
        ContainerUtil.addIfNotNull(result, VirtualFileManager.getInstance().findFileByUrl(url));
      }

      if (added) return;
    }
  }
}
