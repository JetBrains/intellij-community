// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.roots.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
class ModifiableModelCommitterServiceImpl implements ModifiableModelCommitterService {
  private static final Logger LOG = Logger.getInstance(ModifiableModelCommitter.class);

  @Override
  public void multiCommit(@NotNull Collection<? extends ModifiableRootModel> rootModels, @NotNull ModifiableModuleModel moduleModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final List<RootModelImpl> modelsToCommit = getSortedChangedModels(rootModels, moduleModel);

    final List<ModifiableRootModel> modelsToDispose = new SmartList<>(rootModels);
    modelsToDispose.removeAll(modelsToCommit);

    ModuleManagerImpl.commitModelWithRunnable(moduleModel, () -> {
      for (RootModelImpl model : modelsToCommit) {
        ModuleRootManagerImpl.doCommit(model);
      }
      for (ModifiableRootModel model : modelsToDispose) {
        if (model instanceof RootModelImpl) {
          ((RootModelImpl)model).checkModuleExtensionModification();
        }
        model.dispose();
      }
    });
  }

  @NotNull
  private static List<RootModelImpl> getSortedChangedModels(@NotNull Collection<? extends ModifiableRootModel> rootModels, @NotNull ModifiableModuleModel moduleModel) {
    List<RootModelImpl> result = null;
    for (ModifiableRootModel model : rootModels) {
      RootModelImpl rootModel = (RootModelImpl)model;
      if (rootModel.isChanged()) {
        if (result == null) {
          result = new SmartList<>();
        }
        result.add(rootModel);
      }
    }

    if (result == null) {
      return Collections.emptyList();
    }
    if (result.size() > 1) {
      result.sort(createDFSTBuilder(result, moduleModel).comparator());
    }
    return result;
  }

  @NotNull
  private static DFSTBuilder<RootModelImpl> createDFSTBuilder(@NotNull List<? extends RootModelImpl> rootModels, @NotNull ModifiableModuleModel moduleModel) {
    final Map<String, RootModelImpl> nameToModel = new HashMap<>();
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
    InboundSemiGraph<RootModelImpl> graph = new InboundSemiGraph<RootModelImpl>() {
      @NotNull
      @Override
      public Collection<RootModelImpl> getNodes() {
        return allRootModels;
      }

      @NotNull
      @Override
      public Iterator<RootModelImpl> getIn(RootModelImpl rootModel) {
        OrderEnumerator entries = rootModel.orderEntries().withoutSdk().withoutLibraries().withoutModuleSourceEntries();
        List<String> namesList = entries.process(new RootPolicy<List<String>>() {
          @Override
          public List<String> visitModuleOrderEntry(@NotNull ModuleOrderEntry moduleOrderEntry, List<String> strings) {
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

        String[] names = ArrayUtilRt.toStringArray(namesList);
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
    return new DFSTBuilder<>(GraphGenerator.generate(CachingSemiGraph.cache(graph)));
  }
}
