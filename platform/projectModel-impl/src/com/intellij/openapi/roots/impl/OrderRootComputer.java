// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * The `OrderRootComputer` class is responsible for computing the roots for a given {@link OrderRootsEnumeratorImpl}.
 * There are two implementations that differ only by the type of returning values:
 * - {@link ClassicOrderRootComputer} returns a list of VirtualFiles
 * - {@link MutliverseOrderRootComputer} returns a list of {@link com.intellij.openapi.roots.RootEntry}
 */
abstract class OrderRootComputer<RootEntry> {
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

  @NotNull Collection<RootEntry> computeRoots() {
    Collection<RootEntry> result = new LinkedHashSet<>();
    myOrderEnumerator.forEach((orderEntry, customHandlers) -> {
      OrderRootType type = myOrderEntryToRootType.apply(orderEntry);

      if (orderEntry instanceof ModuleSourceOrderEntry) {
        ModuleRootModel rootModel = ((ModuleSourceOrderEntry)orderEntry).getRootModel();
        boolean includeTests = !myOrderEnumerator.isProductionOnly() &&
                               (OrderEnumeratorBase.shouldIncludeTestsFromDependentModulesToTestClasspath(customHandlers) || myOrderEnumerator.isRootModuleModel(rootModel));
        collectModuleRoots(type, rootModel, result, true, includeTests, customHandlers, orderEntry);
      }
      else if (orderEntry instanceof ModuleOrderEntry moduleOrderEntry) {
        Module module = moduleOrderEntry.getModule();
        if (module != null) {
          ModuleRootModel rootModel = myOrderEnumerator.getRootModel(module);
          boolean productionOnTests = ((ModuleOrderEntry)orderEntry).isProductionOnTestDependency();
          boolean includeTests = !myOrderEnumerator.isProductionOnly()
                                 && OrderEnumeratorBase.shouldIncludeTestsFromDependentModulesToTestClasspath(customHandlers)
                                 || productionOnTests;
          collectModuleRoots(type, rootModel, result, !productionOnTests, includeTests, customHandlers, orderEntry);
        }
      }
      else if (orderEntry instanceof LibraryOrSdkOrderEntry) {
        if (myCustomSdkRootProvider != null && orderEntry instanceof JdkOrderEntry) {
          addAll(result, myCustomSdkRootProvider.fun((JdkOrderEntry)orderEntry), orderEntry);
          return true;
        }
        if (addCustomRootsForLibraryOrSdk((LibraryOrSdkOrderEntry)orderEntry, type, result, customHandlers)) {
          return true;
        }
        addAll(result, ((LibraryOrSdkOrderEntry)orderEntry).getRootFiles(type), orderEntry);
      }
      else {
        LOG.error("Unexpected implementation of OrderEntry: " + orderEntry.getClass().getName());
      }
      return true;
    });
    return result;  }

  private void collectModuleRoots(@NotNull OrderRootType type,
                                  ModuleRootModel rootModel,
                                  @NotNull Collection<? super RootEntry> result,
                                  boolean includeProduction,
                                  boolean includeTests,
                                  @NotNull List<? extends OrderEnumerationHandler> customHandlers,
                                  @NotNull OrderEntry orderEntry) {
    if (type.equals(OrderRootType.SOURCES)) {
      if (includeProduction) {
        addAll(result, rootModel.getSourceRoots(includeTests), orderEntry);
      }
      else {
        addAll(result, rootModel.getSourceRoots(JavaModuleSourceRootTypes.TESTS), orderEntry);
      }
    }
    else if (type.equals(OrderRootType.CLASSES)) {
      CompilerModuleExtension extension = rootModel.getModuleExtension(CompilerModuleExtension.class);
      if (extension != null) {
        if (myWithoutSelfModuleOutput && myOrderEnumerator.isRootModuleModel(rootModel)) {
          if (includeTests && includeProduction) {
            addAll(result, extension.getOutputRoots(false), orderEntry);
          }
        }
        else {
          if (includeProduction) {
            addAll(result, extension.getOutputRoots(includeTests), orderEntry);
          }
          else {
            addIfNotNull(result, extension.getCompilerOutputPathForTests(), orderEntry);
          }
        }
      }
    }
    addCustomRootsForModule(type, rootModel, orderEntry, result, includeProduction, includeTests, customHandlers);
  }

  private boolean addCustomRootsForLibraryOrSdk(@NotNull LibraryOrSdkOrderEntry forOrderEntry,
                                                @NotNull OrderRootType type,
                                                @NotNull Collection<? super RootEntry> result,
                                                @NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      List<String> urls = new ArrayList<>();
      boolean added = handler.addCustomRootsForLibraryOrSdk(forOrderEntry, type, urls);
      for (String url : urls) {
        addIfNotNull(result, VirtualFileManager.getInstance().findFileByUrl(url), forOrderEntry);
      }
      if (added) {
        return true;
      }
    }
    return false;
  }

  private void addCustomRootsForModule(@NotNull OrderRootType type,
                                       @NotNull ModuleRootModel rootModel,
                                       @NotNull OrderEntry entry,
                                       @NotNull Collection<? super RootEntry> result,
                                       boolean includeProduction,
                                       boolean includeTests,
                                       @NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      List<String> urls = new ArrayList<>();
      boolean added = handler.addCustomModuleRoots(type, rootModel, urls, includeProduction, includeTests);
      for (String url : urls) {
        addIfNotNull(result, VirtualFileManager.getInstance().findFileByUrl(url), entry);
      }

      if (added) return;
    }
  }

  private void addAll(@NotNull Collection<? super RootEntry> result,
                      VirtualFile @NotNull [] roots,
                      @NotNull OrderEntry orderEntry) {
    for (VirtualFile root : roots) {
      result.add(produceEntry(root, orderEntry));
    }
  }

  private void addAll(@NotNull Collection<? super RootEntry> result,
                      @NotNull Iterable<VirtualFile> roots,
                      @NotNull OrderEntry orderEntry) {
    for (VirtualFile root : roots) {
      result.add(produceEntry(root, orderEntry));
    }
  }

  private void addIfNotNull(@NotNull Collection<? super RootEntry> result,
                            @Nullable VirtualFile root,
                            @NotNull OrderEntry orderEntry) {
    if (root != null) {
      result.add(produceEntry(root, orderEntry));
    }
  }

  protected abstract @NotNull RootEntry produceEntry(@NotNull VirtualFile root, @NotNull OrderEntry orderEntry);
}

class ClassicOrderRootComputer extends OrderRootComputer<VirtualFile> {
  ClassicOrderRootComputer(@NotNull OrderEnumeratorBase enumerator,
                           @NotNull Function<OrderEntry, OrderRootType> entryToRootType,
                           @Nullable NotNullFunction<? super JdkOrderEntry, VirtualFile[]> provider,
                           boolean withoutSelfModuleOutput) {
    super(enumerator, entryToRootType, provider, withoutSelfModuleOutput);
  }

  @Override
  protected @NotNull VirtualFile produceEntry(@NotNull VirtualFile root, @NotNull OrderEntry orderEntry) {
    return root;
  }
}

class MutliverseOrderRootComputer extends OrderRootComputer<RootEntry> {
  MutliverseOrderRootComputer(@NotNull OrderEnumeratorBase enumerator,
                              @NotNull Function<OrderEntry, OrderRootType> entryToRootType,
                              @Nullable NotNullFunction<? super JdkOrderEntry, VirtualFile[]> provider,
                              boolean withoutSelfModuleOutput) {
    super(enumerator, entryToRootType, provider, withoutSelfModuleOutput);
  }

  @Override
  protected @NotNull RootEntry produceEntry(@NotNull VirtualFile root, @NotNull OrderEntry orderEntry) {
    return new RootEntry(root, orderEntry);
  }
}