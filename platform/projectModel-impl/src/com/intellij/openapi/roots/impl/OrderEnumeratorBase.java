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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
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

/**
 * @author nik
 */
abstract class OrderEnumeratorBase extends OrderEnumerator implements OrderEnumeratorSettings {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.OrderEnumeratorBase");
  private boolean myProductionOnly;
  private boolean myCompileOnly;
  private boolean myRuntimeOnly;
  private boolean myWithoutJdk;
  private boolean myWithoutLibraries;
  protected boolean myWithoutDepModules;
  private boolean myWithoutModuleSourceEntries;
  protected boolean myRecursively;
  protected boolean myRecursivelyExportedOnly;
  private boolean myExportedOnly;
  private Condition<OrderEntry> myCondition;
  protected RootModelProvider myModulesProvider;
  private final OrderRootsCache myCache;

  public OrderEnumeratorBase(@Nullable OrderRootsCache cache) {
    myCache = cache;
  }

  static List<OrderEnumerationHandler> getCustomHandlers(@NotNull Module module) {
    List<OrderEnumerationHandler> customHandlers = null;
    for (OrderEnumerationHandler.Factory handlerFactory : OrderEnumerationHandler.EP_NAME.getExtensions()) {
      if (handlerFactory.isApplicable(module)) {
        if (customHandlers == null) {
          customHandlers = new SmartList<>();
        }
        customHandlers.add(handlerFactory.createHandler(module));
      }
    }
    return customHandlers == null ? Collections.<OrderEnumerationHandler>emptyList() : customHandlers;
  }

  @Override
  public OrderEnumerator productionOnly() {
    myProductionOnly = true;
    return this;
  }

  @Override
  public OrderEnumerator compileOnly() {
    myCompileOnly = true;
    return this;
  }

  @Override
  public OrderEnumerator runtimeOnly() {
    myRuntimeOnly = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutSdk() {
    myWithoutJdk = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutLibraries() {
    myWithoutLibraries = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutDepModules() {
    myWithoutDepModules = true;
    return this;
  }

  @Override
  public OrderEnumerator withoutModuleSourceEntries() {
    myWithoutModuleSourceEntries = true;
    return this;
  }

  @Override
  public OrderEnumerator recursively() {
    myRecursively = true;
    return this;
  }

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

  @Override
  public OrderEnumerator satisfying(Condition<OrderEntry> condition) {
    myCondition = condition;
    return this;
  }

  @Override
  public OrderEnumerator using(@NotNull RootModelProvider provider) {
    myModulesProvider = provider;
    return this;
  }

  @Override
  public OrderRootsEnumerator classes() {
    return new OrderRootsEnumeratorImpl(this, OrderRootType.CLASSES);
  }

  @Override
  public OrderRootsEnumerator sources() {
    return new OrderRootsEnumeratorImpl(this, OrderRootType.SOURCES);
  }

  @Override
  public OrderRootsEnumerator roots(@NotNull OrderRootType rootType) {
    return new OrderRootsEnumeratorImpl(this, rootType);
  }

  @Override
  public OrderRootsEnumerator roots(@NotNull NotNullFunction<OrderEntry, OrderRootType> rootTypeProvider) {
    return new OrderRootsEnumeratorImpl(this, rootTypeProvider);
  }

  ModuleRootModel getRootModel(Module module) {
    if (myModulesProvider != null) {
      return myModulesProvider.getRootModel(module);
    }
    return ModuleRootManager.getInstance(module);
  }

  public OrderRootsCache getCache() {
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
  public boolean shouldRecurse(@NotNull ModuleOrderEntry entry, @NotNull List<OrderEnumerationHandler> handlers) {
    ProcessEntryAction action = shouldAddOrRecurse(entry, true, handlers);
    return action.type == ProcessEntryActionType.RECURSE;
  }

  // Should process, should recurse, or not process at all.
  protected enum ProcessEntryActionType {
    SKIP,
    RECURSE,
    PROCESS
  }

  protected static class ProcessEntryAction {
    public ProcessEntryActionType type;
    @Nullable public Module recurseOnModule;

    private ProcessEntryAction(ProcessEntryActionType type) {
      this.type = type;
    }

    public static final ProcessEntryAction SKIP = new ProcessEntryAction(ProcessEntryActionType.SKIP);

    public static ProcessEntryAction RECURSE(@NotNull Module module) {
      ProcessEntryAction result = new ProcessEntryAction(ProcessEntryActionType.RECURSE);
      result.recurseOnModule = module;
      return result;
    }

    public static final ProcessEntryAction PROCESS = new ProcessEntryAction(ProcessEntryActionType.PROCESS);
  }

  protected ProcessEntryAction shouldAddOrRecurse(OrderEntry entry, boolean firstLevel, List<OrderEnumerationHandler> customHandlers) {
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
    if (entry instanceof ExportableOrderEntry) {
      ExportableOrderEntry exportableEntry = (ExportableOrderEntry)entry;
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
    if (myRecursively && entry instanceof ModuleOrderEntry) {
      ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
      final Module depModule = moduleOrderEntry.getModule();
      if (depModule != null && shouldProcessRecursively(customHandlers)) {
        return ProcessEntryAction.RECURSE(depModule);
      }
    }
    if (myWithoutDepModules && entry instanceof ModuleOrderEntry) return ProcessEntryAction.SKIP;
    return ProcessEntryAction.PROCESS;
  }

  protected void processEntries(final ModuleRootModel rootModel, PairProcessor<OrderEntry, List<OrderEnumerationHandler>> processor,
                                Set<Module> processed, boolean firstLevel, final List<OrderEnumerationHandler> customHandlers) {
    if (processed != null && !processed.add(rootModel.getModule())) return;

    for (OrderEntry entry : rootModel.getOrderEntries()) {
      ProcessEntryAction action = shouldAddOrRecurse(entry, firstLevel, customHandlers);
      if (action.type == ProcessEntryActionType.SKIP) {
        continue;
      }
      if (action.type == ProcessEntryActionType.RECURSE) {
        processEntries(getRootModel(action.recurseOnModule), processor, processed, false, customHandlers);
        continue;
      }
      assert action.type == ProcessEntryActionType.PROCESS;
      if (!processor.process(entry, customHandlers)) {
        return;
      }
    }
  }

  private static boolean shouldAddRuntimeDependenciesToTestCompilationClasspath(final List<OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      if (handler.shouldAddRuntimeDependenciesToTestCompilationClasspath()) {
        return true;
      }
    }
    return false;
  }

  private static boolean shouldProcessRecursively(final List<OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      if (!handler.shouldProcessDependenciesRecursively()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void forEach(@NotNull final Processor<OrderEntry> processor) {
    forEach((entry, handlers) -> processor.process(entry));
  }

  protected abstract void forEach(@NotNull PairProcessor<OrderEntry, List<OrderEnumerationHandler>> processor);

  @Override
  public void forEachLibrary(@NotNull final Processor<Library> processor) {
    forEach((entry, handlers) -> {
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
  public void forEachModule(@NotNull final Processor<Module> processor) {
    forEach((orderEntry, customHandlers) -> {
      if (myRecursively && orderEntry instanceof ModuleSourceOrderEntry) {
        final Module module = ((ModuleSourceOrderEntry)orderEntry).getRootModel().getModule();
        return processor.process(module);
      }
      if (orderEntry instanceof ModuleOrderEntry && (!myRecursively || !shouldProcessRecursively(customHandlers))) {
        final Module module = ((ModuleOrderEntry)orderEntry).getModule();
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

  boolean shouldIncludeTestsFromDependentModulesToTestClasspath(List<OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      if (!handler.shouldIncludeTestsFromDependentModulesToTestClasspath()) {
        return false;
      }
    }
    return true;
  }

  boolean addCustomRootsForLibrary(OrderEntry forOrderEntry, OrderRootType type, Collection<VirtualFile> result,
                                   List<OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      final List<String> urls = new ArrayList<>();
      final boolean added =
        handler.addCustomRootsForLibrary(forOrderEntry, type, urls);
      for (String url : urls) {
        ContainerUtil.addIfNotNull(result, VirtualFileManager.getInstance().findFileByUrl(url));
      }
      if (added) {
        return true;
      }
    }
    return false;
  }

  boolean addCustomRootUrlsForLibrary(OrderEntry forOrderEntry, OrderRootType type, Collection<String> result,
                                      List<OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      final List<String> urls = new ArrayList<>();
      final boolean added =
        handler.addCustomRootsForLibrary(forOrderEntry, type, urls);
      result.addAll(urls);
      if (added) {
        return true;
      }
    }
    return false;
  }

  boolean addCustomRootsForModule(OrderRootType type,
                                  ModuleRootModel rootModel,
                                  Collection<VirtualFile> result,
                                  boolean includeProduction,
                                  boolean includeTests,
                                  final List<OrderEnumerationHandler> customHandlers) {
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

  boolean addCustomRootUrlsForModule(OrderRootType type,
                                     ModuleRootModel rootModel,
                                     Collection<String> result,
                                     boolean includeProduction,
                                     boolean includeTests,
                                     List<OrderEnumerationHandler> customHandlers) {
    for (OrderEnumerationHandler handler : customHandlers) {
      final List<String> urls = new ArrayList<>();
      final boolean added = handler.addCustomModuleRoots(type, rootModel, urls, includeProduction, includeTests);
      result.addAll(urls);

      if (added) return true;
    }
    return false;
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
  public abstract void processRootModules(@NotNull Processor<Module> processor);

  private static class OrderEntryProcessor<R> implements PairProcessor<OrderEntry, List<OrderEnumerationHandler>> {
    private R myValue;
    private final RootPolicy<R> myPolicy;

    private OrderEntryProcessor(RootPolicy<R> policy, R initialValue) {
      myPolicy = policy;
      myValue = initialValue;
    }

    @Override
    public boolean process(OrderEntry orderEntry, List<OrderEnumerationHandler> customHandlers) {
      myValue = orderEntry.accept(myPolicy, myValue);
      return true;
    }
  }
}
