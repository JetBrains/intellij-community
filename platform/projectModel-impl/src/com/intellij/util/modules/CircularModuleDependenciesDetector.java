// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.modules;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.Couple;
import com.intellij.util.Chunk;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class CircularModuleDependenciesDetector {
  @NotNull
  private static <T extends ModuleRootModel> Graph<T> createGraphGenerator(@NotNull Map<Module, T> models) {
    return GraphGenerator.generate(CachingSemiGraph.cache(new InboundSemiGraph<T>() {
      @NotNull
      @Override
      public Collection<T> getNodes() {
        return models.values();
      }

      @NotNull
      @Override
      public Iterator<T> getIn(final ModuleRootModel model) {
        final List<T> dependencies = new ArrayList<>();
        model.orderEntries().compileOnly().forEachModule(module -> {
          T depModel = models.get(module);
          if (depModel != null) {
            dependencies.add(depModel);
          }
          return true;
        });
        return dependencies.iterator();
      }
    }));
  }

  @NotNull
  private static <T extends ModuleRootModel> Collection<Chunk<T>> buildChunks(@NotNull Map<Module, T> models) {
    return GraphAlgorithms.getInstance().computeSCCGraph(createGraphGenerator(models)).getNodes();
  }
  /**
   * @return pair of modules which become circular after adding dependency, or null if all remains OK
   */
  @Nullable
  public static Couple<Module> addingDependencyFormsCircularity(@NotNull Module currentModule, @NotNull Module toDependOn) {
    assert currentModule != toDependOn;
    // whatsa lotsa of @&#^%$ codes-a!

    final Map<Module, ModifiableRootModel> models = new LinkedHashMap<>();
    Project project = currentModule.getProject();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      models.put(module, model);
    }
    ModifiableRootModel currentModel = models.get(currentModule);
    ModifiableRootModel toDependOnModel = models.get(toDependOn);
    Collection<Chunk<ModifiableRootModel>> nodesBefore = buildChunks(models);
    for (Chunk<ModifiableRootModel> chunk : nodesBefore) {
      if (chunk.containsNode(toDependOnModel) && chunk.containsNode(currentModel)) return null; // they circular already
    }

    try {
      currentModel.addModuleOrderEntry(toDependOn);
      Collection<Chunk<ModifiableRootModel>> nodesAfter = buildChunks(models);
      for (Chunk<ModifiableRootModel> chunk : nodesAfter) {
        if (chunk.containsNode(toDependOnModel) && chunk.containsNode(currentModel)) {
          List<ModifiableRootModel> nodes = ContainerUtil.collect(chunk.getNodes().iterator());
          // graph algorithms collections are inherently unstable, so sort to return always the same modules to avoid blinking tests
          nodes.sort(Comparator.comparing(m -> m.getModule().getName()));
          return Couple.of(nodes.get(0).getModule(), nodes.get(1).getModule());
        }
      }
    }
    finally {
      for (ModifiableRootModel model : models.values()) {
        model.dispose();
      }
    }
    return null;
  }
}
