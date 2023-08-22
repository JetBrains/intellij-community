// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public final class ModuleOrderEnumerator extends OrderEnumeratorBase {
  private final ModuleRootModel myRootModel;

  @ApiStatus.Internal
  public ModuleOrderEnumerator(@NotNull ModuleRootModel rootModel, @Nullable OrderRootsCache cache) {
    super(cache);
    myRootModel = rootModel;
  }

  @Override
  public void processRootModules(@NotNull Processor<? super Module> processor) {
    processor.process(myRootModel.getModule());
  }

  @Override
  protected void forEach(@NotNull PairProcessor<? super OrderEntry, ? super List<? extends OrderEnumerationHandler>> processor) {
    processEntries(myRootModel, myRecursively ? CollectionFactory.createSmallMemoryFootprintSet() : null, true, getCustomHandlers(myRootModel.getModule()), processor);
  }

  @Override
  public boolean isRootModuleModel(@NotNull ModuleRootModel rootModel) {
    return rootModel.getModule() == myRootModel.getModule();
  }
}

