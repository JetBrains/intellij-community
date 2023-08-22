// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ProjectOrderEnumerator extends OrderEnumeratorBase {
  private final Project myProject;

  ProjectOrderEnumerator(@NotNull Project project, @Nullable OrderRootsCache rootsCache) {
    super(rootsCache);
    myProject = project;
  }

  @Override
  public void processRootModules(@NotNull Processor<? super Module> processor) {
    Module[] modules = myModulesProvider != null ? myModulesProvider.getModules() : ModuleManager.getInstance(myProject).getSortedModules();
    for (Module each : modules) {
      processor.process(each);
    }
  }

  @Override
  protected void forEach(@NotNull final PairProcessor<? super OrderEntry, ? super List<? extends OrderEnumerationHandler>> processor) {
    myRecursively = false;
    myWithoutDepModules = true;
    Set<Module> processed = new HashSet<>();
    processRootModules(module -> {
      processEntries(getRootModel(module), processed, true, getCustomHandlers(module), processor);
      return true;
    });
  }

  @Override
  public void forEachModule(@NotNull Processor<? super Module> processor) {
    processRootModules(processor);
  }

  @Override
  public boolean isRootModuleModel(@NotNull ModuleRootModel rootModel) {
    return true;
  }
}
