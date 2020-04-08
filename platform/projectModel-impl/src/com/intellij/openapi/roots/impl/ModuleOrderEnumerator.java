/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModuleOrderEnumerator extends OrderEnumeratorBase {
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
    processEntries(myRootModel, myRecursively ? new THashSet<>() : null, true, getCustomHandlers(myRootModel.getModule()), processor);
  }

  @Override
  public boolean isRootModuleModel(@NotNull ModuleRootModel rootModel) {
    return rootModel.getModule() == myRootModel.getModule();
  }
}

