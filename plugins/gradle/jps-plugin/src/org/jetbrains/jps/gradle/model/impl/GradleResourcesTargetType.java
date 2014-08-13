/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.gradle.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.gradle.model.JpsGradleExtensionService;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 7/10/2014
 */
public class GradleResourcesTargetType extends ModuleBasedBuildTargetType<GradleResourcesTarget> {
  public static final GradleResourcesTargetType PRODUCTION = new GradleResourcesTargetType("gradle-resources-production", false);
  public static final GradleResourcesTargetType TEST = new GradleResourcesTargetType("gradle-resources-test", true);

  private final boolean myIsTests;

  private GradleResourcesTargetType(final String typeId, boolean isTests) {
    super(typeId);
    myIsTests = isTests;
  }

  public boolean isTests() {
    return myIsTests;
  }

  @NotNull
  @Override
  public List<GradleResourcesTarget> computeAllTargets(@NotNull JpsModel model) {
    final List<GradleResourcesTarget> targets = new ArrayList<GradleResourcesTarget>();
    for (JpsModule module : model.getProject().getModules()) {
      if (JpsGradleExtensionService.getInstance().getExtension(module) != null) {
        targets.add(new GradleResourcesTarget(this, module));
      }
    }
    return targets;
  }

  @NotNull
  @Override
  public BuildTargetLoader<GradleResourcesTarget> createLoader(@NotNull JpsModel model) {
    final Map<String, JpsModule> modules = new HashMap<String, JpsModule>();
    for (JpsModule module : model.getProject().getModules()) {
      modules.put(module.getName(), module);
    }
    return new BuildTargetLoader<GradleResourcesTarget>() {
      @Nullable
      @Override
      public GradleResourcesTarget createTarget(@NotNull String targetId) {
        final JpsModule module = modules.get(targetId);
        return module != null ? new GradleResourcesTarget(GradleResourcesTargetType.this, module) : null;
      }
    };
  }
}
