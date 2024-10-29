// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleManagerEx;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

abstract class OrderEnumeratorBase extends OrderEnumerator implements OrderEnumeratorSettings {
  private static final Logger LOG = Logger.getInstance(OrderEnumeratorBase.class);
  private boolean myProductionOnly;
  private boolean myCompileOnly;
  private boolean myRuntimeOnly;
  private boolean myWithoutJdk;
  private boolean myWithoutLibraries;
  boolean myWithoutDepModules;
  private boolean myWithoutModuleSourceEntries;
  boolean myRecursively;
  boolean myRecursivelyExportedOnly;
  private boolean myExportedOnly;
  private Condition<? super OrderEntry> myCondition;
  RootModelProvider myModulesProvider;
  private final Project myProject;
  private final OrderRootsCache myCache;
  // A map is quicker than querying the workspace model repeatedly.
  private Map<String, Module> myModulesByNameMap;

  OrderEnumeratorBase(@NotNull Project project, @Nullable OrderRootsCache cache) {
    myProject = project;
    myCache = cache;
  }

  @NotNull
  static List<OrderEnumerationHandler> getCustomHandlers(@NotNull Module module) {
    List<OrderEnumerationHandler> customHandlers = null;
    for (OrderEnumerationHandler.Factory handlerFactory : OrderEnumerationHandler.EP_NAME.getExtensionList()) {
      if (handlerFactory.isApplicable(module)) {
        if (customHandlers == null) {
          customHandlers = new SmartList<>();
        }
        customHandlers.add(handlerFactory.createHandler(module));
      }
    }
    return customHandlers == null ? Collections.emptyList() : customHandlers;
  }

  @NotNull
  @Override
  public OrderEnumerator productionOnly() {
    myProductionOnly = true;
    return this;
  }

  @NotNull
  @Override
  public OrderEnumerator compileOnly() {
    myCompileOnly = true;
    return this;
  }

  @NotNull
  @Override
  public OrderEnumerator runtimeOnly() {
    myRuntimeOnly = true;
    return this;
  }

  @NotNull
  @Override
  public OrderEnumerator withoutSdk() {
    myWithoutJdk = true;
    return this;
  }

  @NotNull
  @Override
  public OrderEnumerator withoutLibraries() {
    myWithoutLibraries = true;
    return this;
  }

  @NotNull
  @Override
  public OrderEnumerator withoutDepModules() {
    myWithoutDepModules = true;
    return this;
  }

  @NotNull
  @Override
  public OrderEnumerator withoutModuleSourceEntries() {
    myWithoutModuleSourceEntries = true;
    return this;
  }

  @NotNull
  @Override
  public OrderEnumerator recursively() {
    myRecursively = true;
    return this;
  }

  @NotNull
  @Override
  public OrderEnumerator exportedOnly() {
    if (myRecursively) {
      myRecursivelyExportedOnly = true;
    }
    else {
      myExportedOnly = true;
    }
    return this;
  }

  @NotNull
  @Override
  public OrderEnumerator satisfying(@NotNull Condition<? super OrderEntry> condition) {
    myCondition = condition;
    return this;
  }

  @NotNull
  @Override
  public OrderEnumerator using(@NotNull RootModelProvider provider) {
    myModulesProvider = provider;
    return this;
  }

  @NotNull
  @Override
  public OrderRootsEnumerator classes() {
    return new OrderRootsEnumeratorImpl(this, OrderRootType.CLASSES);
  }

  @NotNull
  @Override
  public OrderRootsEnumerator sources() {
    return new OrderRootsEnumeratorImpl(this, OrderRootType.SOURCES);
  }

  @NotNull
  @Override
  public OrderRootsEnumerator roots(@NotNull OrderRootType rootType) {
    return new OrderRootsEnumeratorImpl(this, rootType);
  }

  @NotNull
  @Override
  public OrderRootsEnumerator roots(@NotNull NotNullFunction<? super OrderEntry, ? extends OrderRootType> rootTypeProvider) {
    return new OrderRootsEnumeratorImpl(this, rootTypeProvider);
  }

  ModuleRootModel getRootModel(@NotNull Module module) {
    if (myModulesProvider != null) {
      return myModulesProvider.getRootModel(module);
    }
    return ModuleRootManager.getInstance(module);
  }

  @NotNull
  OrderRootsCache getCache() {
    LOG.assertTrue(myCache != null, "Caching is not supported for ModifiableRootModel");
    LOG.assertTrue(myCondition == null, "Caching not supported for OrderEnumerator with 'satisfying(Condition)' option");
    LOG.assertTrue(myModulesProvider == null, "Caching not supported for OrderEnumerator with 'using(ModulesProvider)' option");
    return myCache;
  }

  public int getFlags() {
    int flags = 0;
    if (myProductionOnly) flags |= 1;
    flags <<= 1;
    if (myCompileOnly) flags |= 1;
    flags <<= 1;
    if (myRuntimeOnly) flags |= 1;
    flags <<= 1;
    if (myWithoutJdk) flags |= 1;
    flags <<= 1;
    if (myWithoutLibraries) flags |= 1;
    flags <<= 1;
    if (myWithoutDepModules) flags |= 1;
    flags <<= 1;
    if (myWithoutModuleSourceEntries) flags |= 1;
    flags <<= 1;
    if (myRecursively) flags |= 1;
    flags <<= 1;
    if (myRecursivelyExportedOnly) flags |= 1;
    flags <<= 1;
    if (myExportedOnly) flags |= 1;
    return flags;
  }

  @Override
  public boolean shouldRecurse(@NotNull ModuleOrderEntry entry, @NotNull List<? extends OrderEnumerationHandler> handlers) {
    updateModulesByNameMap();
    ProcessEntryAction action = shouldAddOrRecurse(entry, true, handlers);
    return action.type == ProcessEntryActionType.RECURSE;
  }

  // Should process, should recurse, or not process at all.
  protected enum ProcessEntryActionType {
    SKIP,
    RECURSE,
    PROCESS
  }

  protected static final class ProcessEntryAction {
    @NotNull
    public ProcessEntryActionType type;
    @Nullable Module recurseOnModule;

    private ProcessEntryAction(@NotNull ProcessEntryActionType type) {
      this.type = type;
    }

    public static final ProcessEntryAction SKIP = new ProcessEntryAction(ProcessEntryActionType.SKIP);

    @NotNull
    static ProcessEntryAction RECURSE(@NotNull Module module) {
      ProcessEntryAction result = new ProcessEntryAction(ProcessEntryActionType.RECURSE);
      result.recurseOnModule = module;
      return result;
    }

    public static final ProcessEntryAction PROCESS = new ProcessEntryAction(ProcessEntryActionType.PROCESS);
  }

  @NotNull
  private ProcessEntryAction shouldAddOrRecurse(@NotNull OrderEntry entry, boolean firstLevel, @NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    if (myCondition != null && !myCondition.value(entry)) return ProcessEntryAction.SKIP;

    if (entry instanceof JdkOrderEntry && (myWithoutJdk || !firstLevel)) return ProcessEntryAction.SKIP;
    if (myWithoutLibraries && entry instanceof LibraryOrderEntry) return ProcessEntryAction.SKIP;
    if (myWithoutDepModules) {
      if (!myRecursively && entry instanceof ModuleOrderEntry) return ProcessEntryAction.SKIP;
      if (entry instanceof ModuleSourceOrderEntry && !isRootModuleModel(((ModuleSourceOrderEntry)entry).getRootModel())) {
        return ProcessEntryAction.SKIP;
      }
    }
    if (myWithoutModuleSourceEntries && entry instanceof ModuleSourceOrderEntry) return ProcessEntryAction.SKIP;

    OrderEnumerationHandler.AddDependencyType shouldAdd = OrderEnumerationHandler.AddDependencyType.DEFAULT;
    for (OrderEnumerationHandler handler : customHandlers) {
      shouldAdd = handler.shouldAddDependency(entry, this);
      if (shouldAdd != OrderEnumerationHandler.AddDependencyType.DEFAULT) break;
    }
    if (shouldAdd == OrderEnumerationHandler.AddDependencyType.DO_NOT_ADD) {
      return ProcessEntryAction.SKIP;
    }

    boolean exported = !(entry instanceof JdkOrderEntry);
    if (entry instanceof ExportableOrderEntry exportableEntry) {
      if (shouldAdd == OrderEnumerationHandler.AddDependencyType.DEFAULT) {
        final DependencyScope scope = exportableEntry.getScope();
        boolean forTestCompile = scope.isForTestCompile() ||
                                 scope == DependencyScope.RUNTIME && shouldAddRuntimeDependenciesToTestCompilationClasspath(customHandlers);
        if (myCompileOnly && !scope.isForProductionCompile() && !forTestCompile) return ProcessEntryAction.SKIP;
        if (myRuntimeOnly && !scope.isForProductionRuntime() && !scope.isForTestRuntime()) return ProcessEntryAction.SKIP;
        if (myProductionOnly) {
          if (!scope.isForProductionCompile() && !scope.isForProductionRuntime() ||
              myCompileOnly && !scope.isForProductionCompile() ||
              myRuntimeOnly && !scope.isForProductionRuntime()) {
            return ProcessEntryAction.SKIP;
          }
        }
      }
      exported = exportableEntry.isExported();
    }
    if (!exported) {
      if (myExportedOnly) return ProcessEntryAction.SKIP;
      if (myRecursivelyExportedOnly && !firstLevel) return ProcessEntryAction.SKIP;
    }
    if (myRecursively && entry instanceof ModuleOrderEntry moduleOrderEntry) {
      final Module depModule = myModulesByNameMap.get(moduleOrderEntry.getModuleName());
      if (depModule != null && shouldProcessRecursively(customHandlers)) {
        return ProcessEntryAction.RECURSE(depModule);
      }
    }
    if (myWithoutDepModules && entry instanceof ModuleOrderEntry) return ProcessEntryAction.SKIP;
    return ProcessEntryAction.PROCESS;
  }

  protected void processEntries(@NotNull ModuleRootModel rootModel,
                                @Nullable Set<? super Module> processed,
                                boolean firstLevel,
                                @NotNull List<? extends OrderEnumerationHandler> customHandlers,
                                @NotNull PairProcessor<? super OrderEntry, ? super List<? extends OrderEnumerationHandler>> processor) {
    updateModulesByNameMap();
    doProcessEntries(rootModel, processed, firstLevel, customHandlers, processor);
  }

  private void updateModulesByNameMap() {
    final ModuleManagerEx moduleManager = ModuleManagerEx.getInstanceEx(myProject);
    myModulesByNameMap = moduleManager.getModulesByNameMap();
  }

  private void doProcessEntries(@NotNull ModuleRootModel rootModel,
                                @Nullable Set<? super Module> processed,
                                boolean firstLevel,
                                @NotNull List<? extends OrderEnumerationHandler> customHandlers,
                                @NotNull PairProcessor<? super OrderEntry, ? super List<? extends OrderEnumerationHandler>> processor) {
    ProgressManager.checkCanceled();
    if (processed != null && !processed.add(rootModel.getModule())) return;

    for (OrderEntry entry : rootModel.getOrderEntries()) {
      ProcessEntryAction action = shouldAddOrRecurse(entry, firstLevel, customHandlers);
      if (action.type == ProcessEntryActionType.SKIP) {
        continue;
      }
      if (action.type == ProcessEntryActionType.RECURSE) {
        doProcessEntries(getRootModel(action.recurseOnModule), processed, false, customHandlers, processor);
        continue;
      }
      assert action.type == ProcessEntryActionType.PROCESS;
      if (!processor.process(entry, customHandlers)) {
        return;
      }
    }
  }

  private static boolean shouldAddRuntimeDependenciesToTestCompilationClasspath(@NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      if (handler.shouldAddRuntimeDependenciesToTestCompilationClasspath()) {
        return true;
      }
    }
    return false;
  }

  private static boolean shouldProcessRecursively(@NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      if (!handler.shouldProcessDependenciesRecursively()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void forEach(@NotNull final Processor<? super OrderEntry> processor) {
    forEach((entry, __) -> processor.process(entry));
  }

  protected abstract void forEach(@NotNull PairProcessor<? super OrderEntry, ? super List<? extends OrderEnumerationHandler>> processor);

  @Override
  public void forEachLibrary(@NotNull final Processor<? super Library> processor) {
    forEach((entry, __) -> {
      if (entry instanceof LibraryOrderEntry) {
        final Library library = ((LibraryOrderEntry)entry).getLibrary();
        if (library != null) {
          return processor.process(library);
        }
      }
      return true;
    });
  }

  @Override
  public void forEachModule(@NotNull final Processor<? super Module> processor) {
    updateModulesByNameMap();
    forEach((orderEntry, customHandlers) -> {
      if (myRecursively && orderEntry instanceof ModuleSourceOrderEntry) {
        final Module module = ((ModuleSourceOrderEntry)orderEntry).getRootModel().getModule();
        return processor.process(module);
      }
      if (orderEntry instanceof ModuleOrderEntry && (!myRecursively || !shouldProcessRecursively(customHandlers))) {
        final String moduleName = ((ModuleOrderEntry)orderEntry).getModuleName();
        final Module module = myModulesByNameMap.get(moduleName);
        if (module != null) {
          return processor.process(module);
        }
      }
      return true;
    });
  }

  @Override
  public <R> R process(@NotNull final RootPolicy<R> policy, final R initialValue) {
    final OrderEntryProcessor<R> processor = new OrderEntryProcessor<>(policy, initialValue);
    forEach(processor);
    return processor.myValue;
  }

  static boolean shouldIncludeTestsFromDependentModulesToTestClasspath(@NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      if (!handler.shouldIncludeTestsFromDependentModulesToTestClasspath()) {
        return false;
      }
    }
    return true;
  }

  static boolean addCustomRootUrlsForLibraryOrSdk(@NotNull LibraryOrSdkOrderEntry forOrderEntry,
                                                  @NotNull OrderRootType type,
                                                  @NotNull Collection<? super String> result,
                                                  @NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      final List<String> urls = new ArrayList<>();
      final boolean added =
        handler.addCustomRootsForLibraryOrSdk(forOrderEntry, type, urls);
      result.addAll(urls);
      if (added) {
        return true;
      }
    }
    return false;
  }

  static void addCustomRootsUrlsForModule(@NotNull OrderRootType type,
                                         @NotNull ModuleRootModel rootModel,
                                         @NotNull Collection<String> result,
                                         boolean includeProduction,
                                         boolean includeTests,
                                         @NotNull List<? extends OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      handler.addCustomModuleRoots(type, rootModel, result, includeProduction, includeTests);
    }
  }

  @Override
  public boolean isRuntimeOnly() {
    return myRuntimeOnly;
  }

  @Override
  public boolean isCompileOnly() {
    return myCompileOnly;
  }

  @Override
  public boolean isProductionOnly() {
    return myProductionOnly;
  }

  public boolean isRootModuleModel(@NotNull ModuleRootModel rootModel) {
    return false;
  }

  /**
   * Runs processor on each module that this enumerator was created on.
   *
   * @param processor processor
   */
  public abstract void processRootModules(@NotNull Processor<? super Module> processor);

  private static final class OrderEntryProcessor<R> implements PairProcessor<OrderEntry, List<? extends OrderEnumerationHandler>> {
    private R myValue;
    private final RootPolicy<R> myPolicy;

    private OrderEntryProcessor(@NotNull RootPolicy<R> policy, R initialValue) {
      myPolicy = policy;
      myValue = initialValue;
    }

    @Override
    public boolean process(OrderEntry orderEntry, List<? extends OrderEnumerationHandler> __) {
      myValue = orderEntry.accept(myPolicy, myValue);
      return true;
    }
  }
}
