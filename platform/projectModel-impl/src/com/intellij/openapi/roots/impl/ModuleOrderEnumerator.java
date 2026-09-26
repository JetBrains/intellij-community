// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class ModuleOrderEnumerator extends OrderEnumeratorBase {
  private final ModuleRootModel myRootModel;
  private @Nullable Set<? super Module> myProcessedModules;

  @ApiStatus.Internal
  public ModuleOrderEnumerator(@NotNull ModuleRootModel rootModel, @Nullable OrderRootsCache cache) {
    super(rootModel.getModule().getProject(), cache);
    myRootModel = rootModel;
  }

  @Override
  public void processRootModules(@NotNull Processor<? super Module> processor) {
    processor.process(myRootModel.getModule());
  }

  @Override
  protected void forEach(@NotNull PairProcessor<? super OrderEntry, ? super List<? extends OrderEnumerationHandler>> processor) {
    Set<? super Module> processedModules = myProcessedModules;
    if (myRecursively && processedModules == null) {
      processedModules = CollectionFactory.createSmallMemoryFootprintSet();
    }
    processEntries(myRootModel, processedModules, true, getCustomHandlers(myRootModel.getModule()), processor);
  }

  @Override
  public boolean isRootModuleModel(@NotNull ModuleRootModel rootModel) {
    return rootModel.getModule() == myRootModel.getModule();
  }

  /**
   * @param processedModules set of modules that should be skipped during enumeration because they have been processed already elsewhere.
   * <b>After enumeration all the visited modules will be added to this set.</b>
   * @return this instance
   */
  public @NotNull ModuleOrderEnumerator withProcessedModules(@Nullable Set<? super Module> processedModules) {
    this.myProcessedModules = processedModules;
    return this;
  }
}

