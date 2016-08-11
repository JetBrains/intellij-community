/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class ProjectOrderEnumerator extends OrderEnumeratorBase {
  private final Project myProject;

  public ProjectOrderEnumerator(Project project, OrderRootsCache rootsCache) {
    super(rootsCache);
    myProject = project;
  }

  @Override
  public void processRootModules(@NotNull Processor<Module> processor) {
    Module[] modules = myModulesProvider != null ? myModulesProvider.getModules() : ModuleManager.getInstance(myProject).getSortedModules();
    for (Module each : modules) {
      processor.process(each);
    }
  }

  @Override
  protected void forEach(@NotNull final PairProcessor<OrderEntry, List<OrderEnumerationHandler>> processor) {
    myRecursively = false;
    myWithoutDepModules = true;
    final THashSet<Module> processed = new THashSet<>();
    processRootModules(module -> {
      processEntries(getRootModel(module), processor, processed, true, getCustomHandlers(module));
      return true;
    });
  }

  @Override
  public boolean isRootModuleModel(@NotNull ModuleRootModel rootModel) {
    return true;
  }
}
