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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.roots.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ModifiableModelCommitter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModifiableModelCommitter");

  public static void multiCommit(@NotNull ModifiableRootModel[] rootModels, @NotNull ModifiableModuleModel moduleModel) {
    multiCommit(Arrays.asList(rootModels), moduleModel);
  }

  public static void multiCommit(@NotNull Collection<ModifiableRootModel> rootModels, @NotNull ModifiableModuleModel moduleModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final List<RootModelImpl> modelsToCommit = getSortedChangedModels(rootModels, moduleModel);

    final List<ModifiableRootModel> modelsToDispose = ContainerUtil.newArrayList(rootModels);
    modelsToDispose.removeAll(modelsToCommit);

    ModuleManagerImpl.commitModelWithRunnable(moduleModel, () -> {
      for (RootModelImpl model : modelsToCommit) {
        ModuleRootManagerImpl.doCommit(model);
      }
      for (ModifiableRootModel model : modelsToDispose) {
        model.dispose();
      }
    });
  }

  private static List<RootModelImpl> getSortedChangedModels(Collection<ModifiableRootModel> rootModels, ModifiableModuleModel moduleModel) {
    List<RootModelImpl> result = ContainerUtil.newArrayListWithCapacity(rootModels.size());

    for (ModifiableRootModel model : rootModels) {
      RootModelImpl rootModel = (RootModelImpl)model;
      if (rootModel.isChanged()) {
        result.add(rootModel);
      }
    }

    DFSTBuilder<RootModelImpl> builder = createDFSTBuilder(result, moduleModel);
    Collections.sort(result, builder.comparator());

    return result;
  }

  private static DFSTBuilder<RootModelImpl> createDFSTBuilder(List<RootModelImpl> rootModels, final ModifiableModuleModel moduleModel) {
    final Map<String, RootModelImpl> nameToModel = ContainerUtil.newHashMap();
    for (RootModelImpl rootModel : rootModels) {
      String name = rootModel.getModule().getName();
      LOG.assertTrue(!nameToModel.containsKey(name), name);
      nameToModel.put(name, rootModel);
    }

    Module[] modules = moduleModel.getModules();
    for (Module module : modules) {
      String name = module.getName();
      if (!nameToModel.containsKey(name)) {
        RootModelImpl rootModel = ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).getRootModel();
        nameToModel.put(name, rootModel);
      }
    }

    final Collection<RootModelImpl> allRootModels = nameToModel.values();
    GraphGenerator.SemiGraph<RootModelImpl> graph = new GraphGenerator.SemiGraph<RootModelImpl>() {
      @Override
      public Collection<RootModelImpl> getNodes() {
        return allRootModels;
      }

      @Override
      public Iterator<RootModelImpl> getIn(RootModelImpl rootModel) {
        OrderEnumerator entries = rootModel.orderEntries().withoutSdk().withoutLibraries().withoutModuleSourceEntries();
        List<String> namesList = entries.process(new RootPolicy<List<String>>() {
          @Override
          public List<String> visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, List<String> strings) {
            Module module = moduleOrderEntry.getModule();
            if (module != null && !module.isDisposed()) {
              strings.add(module.getName());
            }
            else {
              final Module moduleToBeRenamed = moduleModel.getModuleToBeRenamed(moduleOrderEntry.getModuleName());
              if (moduleToBeRenamed != null && !moduleToBeRenamed.isDisposed()) {
                strings.add(moduleToBeRenamed.getName());
              }
            }
            return strings;
          }
        }, new ArrayList<>());

        String[] names = ArrayUtil.toStringArray(namesList);
        List<RootModelImpl> result = new ArrayList<>();
        for (String name : names) {
          RootModelImpl depRootModel = nameToModel.get(name);
          if (depRootModel != null) { // it is ok not to find one
            result.add(depRootModel);
          }
        }
        return result.iterator();
      }
    };
    return new DFSTBuilder<>(new GraphGenerator<>(new CachingSemiGraph<>(graph)));
  }
}
